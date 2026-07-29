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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /landscape/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}/commentry}.
 *
 * <p>Seed: assmt 11 → dim 1; detail 300 (Open) is the save target, 301 (Locked) is the conflict case.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssmtDetailCommentaryControllerTest {

    private static final String URL =
            "/landscape/assessment/{assmtId}/assessmentDetail/{detailId}/commentry";
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
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,'[]',2,'SG','seed')
                """);
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) "
                + "VALUES(11,1,'July 2026','Open','seed')");
        insertDetail(300, 11, "SG", "Open");
        insertDetail(301, 11, "HK", "Locked");
    }

    private void insertDetail(long id, long assmtId, String location, String status) {
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,LOCATION,category,STATUS,CREATED_BY)
                VALUES(%d,%d,'OR','%s','L2','%s','seed')
                """.formatted(id, assmtId, location, status));
    }

    private String body(String commentry) {
        return "{\"revisedCommentry\":" + (commentry == null ? "null" : "\"" + commentry + "\"") + "}";
    }

    @Test
    void save_valid_persistsCommentaryAndReturnsIt() throws Exception {
        mvc.perform(post(URL, 11, 300).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Reviewed and accepted")))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.success", is(true)))
           .andExpect(jsonPath("$.data.lndscpAssmtId", is(11)))
           .andExpect(jsonPath("$.data.assmtDetailId", is(300)))
           .andExpect(jsonPath("$.data.revisedCommentary", is("Reviewed and accepted")))
           // The reviser (from the header) and the revision timestamp are echoed back.
           .andExpect(jsonPath("$.data.commentaryRevisedBy", is(USER)))
           .andExpect(jsonPath("$.data.commentaryRevisedAt").exists());

        String stored = jdbc.queryForObject(
                "SELECT REVISED_COMMENTARY FROM orl_lndscp_assmt_details WHERE id=300", String.class);
        assert "Reviewed and accepted".equals(stored);
        String revisedBy = jdbc.queryForObject(
                "SELECT COMMENTARY_REVISED_BY FROM orl_lndscp_assmt_details WHERE id=300", String.class);
        assert USER.equals(revisedBy);
        Integer revisedAtSet = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details WHERE id=300 AND COMMENTARY_REVISED_AT IS NOT NULL",
                Integer.class);
        assert revisedAtSet != null && revisedAtSet == 1;
        String updatedBy = jdbc.queryForObject(
                "SELECT UPDATED_BY FROM orl_lndscp_assmt_details WHERE id=300", String.class);
        assert USER.equals(updatedBy);
    }

    @Test
    void save_blank_clearsCommentary() throws Exception {
        mvc.perform(post(URL, 11, 300).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON).content(body(null)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.revisedCommentary").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void save_missingHeader_returns401() throws Exception {
        mvc.perform(post(URL, 11, 300)
                        .contentType(MediaType.APPLICATION_JSON).content(body("x")))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void save_unknownDetail_returns404() throws Exception {
        mvc.perform(post(URL, 11, 8888).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON).content(body("x")))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("8888")));
    }

    @Test
    void save_detailNotOpen_returns409() throws Exception {
        mvc.perform(post(URL, 11, 301).header("X-EGRC-UserId", USER)
                        .contentType(MediaType.APPLICATION_JSON).content(body("x")))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Open")));
    }
}
