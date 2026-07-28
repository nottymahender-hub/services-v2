package com.dbs.mot.grc.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /landscape/{lndscpAssmtId}/{assmtDetailId}/save}.
 *
 * <h3>Seed data</h3>
 * <ul>
 *   <li>Assmt 11 → dim 1; Assmt 12 → dim 1 (to prove the save locates the detail by its
 *       primary key, independent of the path assessment).</li>
 *   <li>Detail 300 (assmt 11, Open) — save target; 301 (assmt 11, Locked) — not editable;
 *       310 (assmt 12, Open) — under a different assessment.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssmtDetailSaveControllerTest {

    private static final String URL = "/landscape/assessment/{assmtId}/assessmentDetail/{detailId}/overlay";
    private static final String USER = "tester";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '[{"groupName":"IT","isGroup":false,"riskAreas":[{"riskArea":"OR","riskClusters":["OR"]}]}]',
                    2,'SG','seed')
                """);
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(11,1,'July 2026','Open','seed')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(12,1,'Aug 2026','Open','seed')");

        // Detail 300 starts with an existing overlay to prove save overwrites/clears it.
        // Distinct LOCATION per row keeps the (assmt, risk area, BU path, location) unique index happy.
        insertDetail(300, 11, "SG", "Open", "High", "old justification");
        insertDetail(301, 11, "HK", "Locked", null, null);
        insertDetail(310, 12, "SG", "Open", null, null);
    }

    private void insertDetail(long id, long assmtId, String location, String status, String ovrly, String jstfkn) {
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,LOCATION,category,STATUS,OVRLY_NET_RISK_RTNG,OVRLY_JSTFKN,CREATED_BY)
                VALUES(%d,%d,'OR',%s,'L2',%s,%s,%s,'seed')
                """.formatted(id, assmtId, q(location), q(status), q(ovrly), q(jstfkn)));
    }

    private String q(String v) {
        return v == null ? "NULL" : "'" + v.replace("'", "''") + "'";
    }

    private String body(String revised, String nrr, String jstfkn) {
        return "{" + field("revisedCommentry", revised) + "," + field("overlaidNRR", nrr) + ","
                + field("overlayJstfkn", jstfkn) + "}";
    }

    private String field(String name, String value) {
        return "\"" + name + "\":" + (value == null ? "null" : "\"" + value + "\"");
    }

    // ── Happy path ──────────────────────────────────────────────────────────────

    @Test
    void save_valid_persistsOverlayAndStampsUpdatedBy() throws Exception {
        mvc.perform(post(URL, 11, 300).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Revised text", "Low", "Overlay reason")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.success", is(true)))
           .andExpect(jsonPath("$.message", containsString("300")))
           // Response echoes the persisted overlay fields + ids (task 7).
           .andExpect(jsonPath("$.data.lndscpAssmtId", is(11)))
           .andExpect(jsonPath("$.data.assmtDetailId", is(300)))
           .andExpect(jsonPath("$.data.overlaidNRR", is("Low")))
           .andExpect(jsonPath("$.data.overlayJstfkn", is("Overlay reason")))
           .andExpect(jsonPath("$.data.status", is("Open")))
           // No previous assessment/fact seeded here → derived change is N.A.
           .andExpect(jsonPath("$.data.riskRatingChange", is("N.A")));

        assertColumn(300, "REVISED_COMMENTARY", "Revised text");
        assertColumn(300, "OVRLY_NET_RISK_RTNG", "Low");
        assertColumn(300, "OVRLY_JSTFKN", "Overlay reason");
        assertColumn(300, "UPDATED_BY", USER);
        Integer stamped = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details WHERE id=300 AND UPDATE_DT_TM IS NOT NULL",
                Integer.class);
        assert stamped != null && stamped == 1;
    }

    @Test
    void save_allNull_clearsOverlay() throws Exception {
        mvc.perform(post(URL, 11, 300).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, null, null)))
           .andExpect(status().isOk());

        Integer cleared = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details WHERE id=300 "
                        + "AND REVISED_COMMENTARY IS NULL AND OVRLY_NET_RISK_RTNG IS NULL AND OVRLY_JSTFKN IS NULL",
                Integer.class);
        assert cleared != null && cleared == 1;
    }

    // ── Auth ──────────────────────────────────────────────────────────────────────

    @Test
    void save_missingHeader_returns401() throws Exception {
        mvc.perform(post(URL, 11, 300)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, null)))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void save_blankHeader_returns401() throws Exception {
        mvc.perform(post(URL, 11, 300).header("X-EGRC-UserId", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, null)))
           .andExpect(status().isUnauthorized());
    }

    // ── Not found ───────────────────────────────────────────────────────────────

    @Test
    void save_unknownAssessment_returns404() throws Exception {
        mvc.perform(post(URL, 9999, 300).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, null)))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", containsString("9999")));
    }

    @Test
    void save_unknownDetail_returns404() throws Exception {
        mvc.perform(post(URL, 11, 8888).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, null)))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", containsString("8888")));
    }

    @Test
    void save_detailIdIsPrimaryKey_savesRegardlessOfPathAssessment() throws Exception {
        // Detail 310 exists under assessment 12. The overlay save now locates the row by its
        // primary key alone and only checks the path assessment for existence (no ownership
        // check), so saving it via assessment 11 succeeds and updates the row.
        mvc.perform(post(URL, 11, 310).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Revised via other assmt", null, null)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.success", is(true)))
           .andExpect(jsonPath("$.message", containsString("310")));

        assertColumn(310, "REVISED_COMMENTARY", "Revised via other assmt");
        assertColumn(310, "UPDATED_BY", USER);
    }

    // ── State / validation ────────────────────────────────────────────────────────

    @Test
    void save_detailNotOpen_returns409() throws Exception {
        mvc.perform(post(URL, 11, 301).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("x", null, null)))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.message", containsString("Open")));
    }

    @Test
    void save_overlaidNRRWithoutJustification_returns400() throws Exception {
        mvc.perform(post(URL, 11, 300).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "Low", null)))
           .andExpect(status().isBadRequest());
    }

    @Test
    void save_justificationWithoutOverlaidNRR_returns400() throws Exception {
        mvc.perform(post(URL, 11, 300).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, null, "reason")))
           .andExpect(status().isBadRequest());
    }

    @Test
    void save_invalidOverlaidNRR_returns400() throws Exception {
        mvc.perform(post(URL, 11, 300).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "Bogus", "reason")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("must be one of")));
    }

    @Test
    void save_overlayJustificationTooLong_returns400() throws Exception {
        String tooLong = "A".repeat(4001);
        mvc.perform(post(URL, 11, 300).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null, "Low", tooLong)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("4000")));
    }

    private void assertColumn(long id, String column, String expected) {
        String actual = jdbc.queryForObject(
                "SELECT " + column + " FROM orl_lndscp_assmt_details WHERE id=" + id, String.class);
        assert expected.equals(actual) : "expected " + column + "='" + expected + "' but was '" + actual + "'";
    }
}
