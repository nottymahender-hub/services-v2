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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link LandscapeAssmtCalloutController}.
 *
 * <h3>Seed data</h3>
 * <ul>
 *   <li>Dim 100: RISK_AREA JSON = {@code {"Cyber Risk":["OR"],"Conduct Risk":["CR"]}},
 *       LOCATIONS = {@code "SG,HK"}, BIZ_UNITS = {@code "Tech,Ops"} (dim columns stay CSV)</li>
 *   <li>Assmt 200 → dim 100</li>
 *   <li>Callout 300 (active, SME='bob'), 301 (soft-deleted) under assmt 200 —
 *       LOCATIONS/BIZ_UNITS stored as JSON arrays</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LandscapeAssmtCalloutControllerTest {

    private static final String BASE_URL  = "/landscape/assessment/{assmtId}/callouts";
    private static final String ITEM_URL  = "/landscape/assessment/{assmtId}/callouts/{calloutId}";
    /** The callouts are now read via the embedded assessment-summary endpoint. */
    private static final String SUMMARY_URL = "/landscape/assessments/{assmtId}";
    private static final String USERNAME  = "testuser";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout_comment_hist");
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(100,'CFG100','Test Landscape',DATE '2024-01-01',1,
                    '[{"groupName":"Cyber","isGroup":false,"riskAreas":[{"riskArea":"Cyber Risk","riskClusters":["OR"]}]},{"groupName":"Conduct","isGroup":false,"riskAreas":[{"riskArea":"Conduct Risk","riskClusters":["CR"]}]}]',
                    'Tech,Ops',2,'SG,HK','seed')
                """);

        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,CREATED_BY)
                VALUES(200,100,'Q1-2024',DATE '2024-01-31','Open','seed')
                """);

        // LOCATIONS/BIZ_UNITS are JSON arrays; comment/SME are NOT NULL.
        jdbc.execute("""
                INSERT INTO orl_lndscp_callout(id,RISK_AREA,LOCATIONS,BIZ_UNITS,lndscp_assmt_id,comment,DEL_FLG,SME,LAST_MODIFIED_SME)
                VALUES(300,'Cyber Risk','["SG","HK"]','["Tech"]',200,'Active callout',FALSE,'bob','bob')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_callout(id,RISK_AREA,LOCATIONS,BIZ_UNITS,lndscp_assmt_id,comment,DEL_FLG,SME,LAST_MODIFIED_SME)
                VALUES(301,'Conduct Risk','["ALL"]','["ALL"]',200,'Deleted callout',TRUE,'bob','bob')
                """);
    }

    private int historyCount(long calloutId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_callout_comment_hist WHERE callout_id=?",
                Integer.class, calloutId);
        return n == null ? 0 : n;
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    @Test
    void getCallouts_returnsOnlyActiveCallouts_withArrayFieldsAndSme() throws Exception {
        mvc.perform(get(SUMMARY_URL, 200).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.callouts", hasSize(1)))
                .andExpect(jsonPath("$.data.callouts[0].id").value(300))
                .andExpect(jsonPath("$.data.callouts[0].riskArea").value("Cyber Risk"))
                .andExpect(jsonPath("$.data.callouts[0].locations", contains("SG", "HK")))
                .andExpect(jsonPath("$.data.callouts[0].bizUnits", contains("Tech")))
                .andExpect(jsonPath("$.data.callouts[0].sme").value("bob"));
    }

    @Test
    void callouts_blockCarriesNoDimensions() throws Exception {
        // Dimension option-sets were removed from the callouts block.
        mvc.perform(get(SUMMARY_URL, 200).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.callouts.dimensions").doesNotExist());
    }

    @Test
    void getCallouts_missingUsername_returns401() throws Exception {
        mvc.perform(get(SUMMARY_URL, 200))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getCallouts_nonExistentAssmt_returns404() throws Exception {
        mvc.perform(get(SUMMARY_URL, 9999).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST ─────────────────────────────────────────────────────────────────

    @Test
    void createCallout_validPayload_returns201_withCreatedCallout() throws Exception {
        String body = """
                {"riskArea":"Conduct Risk","locations":["SG"],"bizUnits":["Ops"],"comment":"New callout","sme":"alice"}
                """;
        // Create returns the inserted callout in $.data.
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Callout created successfully."))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.riskArea").value("Conduct Risk"))
                .andExpect(jsonPath("$.data.locations", contains("SG")))
                .andExpect(jsonPath("$.data.bizUnits", contains("Ops")))
                .andExpect(jsonPath("$.data.comment").value("New callout"))
                .andExpect(jsonPath("$.data.sme").value("alice"))
                .andExpect(jsonPath("$.data.createdOn").exists());

        // Behaviour is verified against the DB.
        Long id = jdbc.queryForObject(
                "SELECT id FROM orl_lndscp_callout WHERE comment='New callout'", Long.class);
        String storedLoc = jdbc.queryForObject(
                "SELECT LOCATIONS FROM orl_lndscp_callout WHERE id=?", String.class, id);
        String sme = jdbc.queryForObject("SELECT SME FROM orl_lndscp_callout WHERE id=?", String.class, id);
        String lastSme = jdbc.queryForObject("SELECT LAST_MODIFIED_SME FROM orl_lndscp_callout WHERE id=?", String.class, id);
        assert "[\"SG\"]".equals(storedLoc) : "expected JSON array, got " + storedLoc;
        // On create the sme is stored as both SME and LAST_MODIFIED_SME.
        assert "alice".equals(sme) && "alice".equals(lastSme);
        assert historyCount(id) == 1;
    }

    @Test
    void timestamps_areFilledByTheDatabase() throws Exception {
        // The app never sets CREATE_DT_TM / UPDATE_DT_TM; the DB fills them (default + ON UPDATE).
        String create = """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"ts test","sme":"alice"}
                """;
        mvc.perform(post(BASE_URL, 200).header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject(
                "SELECT id FROM orl_lndscp_callout WHERE comment='ts test'", Long.class);

        // CREATE_DT_TM populated on insert; UPDATE_DT_TM still null (no update yet).
        assert jdbc.queryForObject("SELECT CREATE_DT_TM FROM orl_lndscp_callout WHERE id=?",
                java.sql.Timestamp.class, id) != null : "CREATE_DT_TM should be DB-populated";
        assert jdbc.queryForObject("SELECT UPDATE_DT_TM FROM orl_lndscp_callout WHERE id=?",
                java.sql.Timestamp.class, id) == null : "UPDATE_DT_TM should be null before any update";

        String update = """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"ts test upd","sme":"bob"}
                """;
        mvc.perform(put(ITEM_URL, 200, id).header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk());

        // UPDATE_DT_TM populated by ON UPDATE CURRENT_TIMESTAMP.
        assert jdbc.queryForObject("SELECT UPDATE_DT_TM FROM orl_lndscp_callout WHERE id=?",
                java.sql.Timestamp.class, id) != null : "UPDATE_DT_TM should be DB-populated after update";
    }

    @Test
    void createCallout_withOthersAndAll_returns201() throws Exception {
        String body = """
                {"riskArea":"Others","locations":["ALL"],"bizUnits":["Others"],"comment":"c","sme":"alice"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.riskArea").value("Others"))
                .andExpect(jsonPath("$.data.locations", contains("ALL")));

        // The Others/ALL sentinel values are accepted and persisted.
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_callout WHERE lndscp_assmt_id=200 AND RISK_AREA='Others'",
                Integer.class);
        assert count != null && count == 1;
    }

    @Test
    void createCallout_commentOver400Chars_returns400() throws Exception {
        // The comment length cap is now enforced by Bean Validation (@Size), returning 400
        // rather than silently truncating.
        String longComment = "A".repeat(401);
        String body = String.format(
                """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"%s","sme":"alice"}
                """, longComment);
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("comment")));
    }

    @Test
    void createCallout_commentExactly400Chars_returns201() throws Exception {
        String maxComment = "A".repeat(400);
        String body = String.format(
                """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"%s","sme":"alice"}
                """, maxComment);
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        Integer len = jdbc.queryForObject(
                "SELECT LENGTH(comment) FROM orl_lndscp_callout WHERE SME='alice' AND RISK_AREA='Cyber Risk'",
                Integer.class);
        assert len != null && len == 400 : "expected 400, got " + len;
    }

    @Test
    void createCallout_missingUsername_returns401() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"c","sme":"alice"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCallout_blankComment_returns400() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"","sme":"alice"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCallout_blankSme_returns400() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"c","sme":""}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCallout_emptyLocations_returns400() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":[],"bizUnits":["Tech"],"comment":"c","sme":"alice"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCallout_nonExistentAssmt_returns404() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"c","sme":"alice"}
                """;
        mvc.perform(post(BASE_URL, 9999)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ── PUT ──────────────────────────────────────────────────────────────────

    @Test
    void updateCallout_shiftsSme_andWritesHistory() throws Exception {
        // Callout 300 currently owned by 'bob'; update with sme='carol'.
        String body = """
                {"riskArea":"Conduct Risk","locations":["HK"],"bizUnits":["Ops"],"comment":"Updated","sme":"carol"}
                """;
        mvc.perform(put(ITEM_URL, 200, 300)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Callout updated successfully."))
                // Response carries the updated callout: new SME 'carol', prior SME shifted.
                .andExpect(jsonPath("$.data.id").value(300))
                .andExpect(jsonPath("$.data.sme").value("carol"))
                .andExpect(jsonPath("$.data.lastModifiedBy").value("bob"))
                .andExpect(jsonPath("$.data.comment").value("Updated"));

        // SME shift and history are verified against the DB: new SME = request sme,
        // LAST_MODIFIED_SME = previous SME ('bob'); locations stored as JSON.
        String sme = jdbc.queryForObject("SELECT SME FROM orl_lndscp_callout WHERE id=300", String.class);
        String lastSme = jdbc.queryForObject("SELECT LAST_MODIFIED_SME FROM orl_lndscp_callout WHERE id=300", String.class);
        String loc = jdbc.queryForObject("SELECT LOCATIONS FROM orl_lndscp_callout WHERE id=300", String.class);
        assert "carol".equals(sme);
        assert "bob".equals(lastSme);
        assert "[\"HK\"]".equals(loc) : "expected JSON array, got " + loc;
        assert historyCount(300) == 1;
    }

    @Test
    void updateCallout_nonExistentCallout_returns404() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"c","sme":"carol"}
                """;
        mvc.perform(put(ITEM_URL, 200, 9999)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCallout_calloutBelongsToDifferentAssmt_returns404() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"c","sme":"carol"}
                """;
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY)
                VALUES(201,100,'Q2-2024','Open','seed')
                """);
        mvc.perform(put(ITEM_URL, 201, 300)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("does not belong")));
    }

    @Test
    void updateCallout_missingUsername_returns401() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":["SG"],"bizUnits":["Tech"],"comment":"c","sme":"carol"}
                """;
        mvc.perform(put(ITEM_URL, 200, 300)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateCallout_anyRiskArea_isAccepted() throws Exception {
        // Callout values are not validated against the landscape dimensions, so any non-blank
        // risk area is accepted and persisted.
        String body = """
                {"riskArea":"Any Area","locations":["SG"],"bizUnits":["Tech"],"comment":"c","sme":"carol"}
                """;
        mvc.perform(put(ITEM_URL, 200, 300)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        String riskArea = jdbc.queryForObject(
                "SELECT RISK_AREA FROM orl_lndscp_callout WHERE id=300", String.class);
        assert "Any Area".equals(riskArea) : "expected updated risk area, got " + riskArea;
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test
    void deleteCallout_returns200AndSoftDeletes() throws Exception {
        mvc.perform(delete(ITEM_URL, 200, 300).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Callout deleted successfully."));

        mvc.perform(get(SUMMARY_URL, 200).header("X-EGRC-UserId", USERNAME))
                .andExpect(jsonPath("$.data.callouts", hasSize(0)));
    }

    @Test
    void deleteCallout_nonExistentCallout_returns404() throws Exception {
        mvc.perform(delete(ITEM_URL, 200, 9999).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCallout_missingUsername_returns401() throws Exception {
        mvc.perform(delete(ITEM_URL, 200, 300))
                .andExpect(status().isUnauthorized());
    }

    // ── Path param type mismatch ──────────────────────────────────────────────

    @Test
    void invalidPathParamType_returns400() throws Exception {
        mvc.perform(delete("/landscape/assessment/abc/callouts/300").header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isBadRequest());
    }
}
