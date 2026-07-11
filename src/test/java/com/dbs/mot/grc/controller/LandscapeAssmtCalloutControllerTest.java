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
 *       LOCATIONS = {@code "SG,HK"}, BIZ_UNITS = {@code "Tech,Ops"}</li>
 *   <li>Assmt 200 → dim 100</li>
 *   <li>Callout 300 (active), 301 (soft-deleted) under assmt 200</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LandscapeAssmtCalloutControllerTest {

    private static final String BASE_URL  = "/landscape/{assmtId}/callouts";
    private static final String ITEM_URL  = "/landscape/{assmtId}/callouts/{calloutId}";
    private static final String USERNAME  = "testuser";

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
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(100,'CFG100','Test Landscape',DATE '2024-01-01',1,
                    '{"Cyber Risk":["OR"],"Conduct Risk":["CR"]}','Tech,Ops',2,'SG,HK','seed')
                """);

        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY)
                VALUES(200,100,'Q1-2024','Open','seed')
                """);

        jdbc.execute("""
                INSERT INTO orl_lndscp_callout(id,RISK_AREA,LOCATIONS,BIZ_UNITS,lndscp_assmt_id,comment,DEL_FLG)
                VALUES(300,'Cyber Risk','SG,HK','Tech',200,'Active callout',FALSE)
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_callout(id,RISK_AREA,LOCATIONS,BIZ_UNITS,lndscp_assmt_id,comment,DEL_FLG)
                VALUES(301,'Conduct Risk','ALL','ALL',200,'Deleted callout',TRUE)
                """);
    }

    // ── GET ──────────────────────────────────────────────────────────────────

    @Test
    void getCallouts_returnsOk() throws Exception {
        mvc.perform(get(BASE_URL, 200).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getCallouts_returnsOnlyActiveCallouts() throws Exception {
        mvc.perform(get(BASE_URL, 200).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.callouts", hasSize(1)))
                .andExpect(jsonPath("$.data.callouts[0].id").value(300))
                .andExpect(jsonPath("$.data.callouts[0].riskArea").value("Cyber Risk"));
    }

    @Test
    void getCallouts_returnsDimensions() throws Exception {
        mvc.perform(get(BASE_URL, 200).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dimensions.validRiskAreas", hasItems("Cyber Risk", "Conduct Risk", "Others")))
                .andExpect(jsonPath("$.data.dimensions.validLocations", hasItems("SG", "HK", "Others", "ALL")))
                .andExpect(jsonPath("$.data.dimensions.validBizUnits",  hasItems("Tech", "Ops", "Others", "ALL")));
    }

    @Test
    void getCallouts_missingUsername_returns401() throws Exception {
        mvc.perform(get(BASE_URL, 200))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getCallouts_blankUsername_returns401() throws Exception {
        mvc.perform(get(BASE_URL, 200).header("X-EGRC-UserId", "  "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCallouts_nonExistentAssmt_returns404() throws Exception {
        mvc.perform(get(BASE_URL, 9999).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── POST ─────────────────────────────────────────────────────────────────

    @Test
    void createCallout_validPayload_returns201() throws Exception {
        String body = """
                {"riskArea":"Conduct Risk","locations":"SG","bizUnits":"Ops","comment":"New callout"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.riskArea").value("Conduct Risk"))
                .andExpect(jsonPath("$.data.locations").value("SG"))
                .andExpect(jsonPath("$.data.bizUnits").value("Ops"));
    }

    @Test
    void createCallout_withOthersAndAll_returns201() throws Exception {
        String body = """
                {"riskArea":"Others","locations":"ALL","bizUnits":"Others"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.riskArea").value("Others"));
    }

    @Test
    void createCallout_commentTruncatedTo400Chars() throws Exception {
        String longComment = "A".repeat(500);
        String body = String.format(
                """
                {"riskArea":"Cyber Risk","locations":"SG","bizUnits":"Tech","comment":"%s"}
                """, longComment);
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.comment", hasLength(400)));
    }

    @Test
    void createCallout_missingUsername_returns401() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":"SG","bizUnits":"Tech"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createCallout_blankRiskArea_returns400() throws Exception {
        String body = """
                {"riskArea":"","locations":"SG","bizUnits":"Tech"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createCallout_nullLocations_returns400() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","bizUnits":"Tech"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createCallout_invalidRiskArea_returns400() throws Exception {
        String body = """
                {"riskArea":"Not A Valid Area","locations":"SG","bizUnits":"Tech"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("RISK_AREA")));
    }

    @Test
    void createCallout_invalidLocation_returns400() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":"InvalidLoc","bizUnits":"Tech"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("LOCATIONS")));
    }

    @Test
    void createCallout_invalidBizUnit_returns400() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":"SG","bizUnits":"InvalidBU"}
                """;
        mvc.perform(post(BASE_URL, 200)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("BIZ_UNITS")));
    }

    @Test
    void createCallout_nonExistentAssmt_returns404() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":"SG","bizUnits":"Tech"}
                """;
        mvc.perform(post(BASE_URL, 9999)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ── PUT ──────────────────────────────────────────────────────────────────

    @Test
    void updateCallout_validPayload_returns200() throws Exception {
        String body = """
                {"riskArea":"Conduct Risk","locations":"HK","bizUnits":"Ops","comment":"Updated"}
                """;
        mvc.perform(put(ITEM_URL, 200, 300)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.riskArea").value("Conduct Risk"))
                .andExpect(jsonPath("$.data.comment").value("Updated"));
    }

    @Test
    void updateCallout_nonExistentCallout_returns404() throws Exception {
        String body = """
                {"riskArea":"Cyber Risk","locations":"SG","bizUnits":"Tech"}
                """;
        mvc.perform(put(ITEM_URL, 200, 9999)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateCallout_calloutBelongsToDifferentAssmt_returns404() throws Exception {
        // assmt 999 doesn't exist, but callout 300 belongs to assmt 200
        String body = """
                {"riskArea":"Cyber Risk","locations":"SG","bizUnits":"Tech"}
                """;
        // create another assmt pointing to the same dim
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
                {"riskArea":"Cyber Risk","locations":"SG","bizUnits":"Tech"}
                """;
        mvc.perform(put(ITEM_URL, 200, 300)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateCallout_invalidRiskArea_returns400() throws Exception {
        String body = """
                {"riskArea":"Bad Area","locations":"SG","bizUnits":"Tech"}
                """;
        mvc.perform(put(ITEM_URL, 200, 300)
                        .header("X-EGRC-UserId", USERNAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    @Test
    void deleteCallout_returns200AndSoftDeletes() throws Exception {
        mvc.perform(delete(ITEM_URL, 200, 300).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Callout deleted successfully."));

        // Verify the callout is no longer returned in GET
        mvc.perform(get(BASE_URL, 200).header("X-EGRC-UserId", USERNAME))
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

    @Test
    void deleteCallout_nonExistentAssmt_returns404() throws Exception {
        mvc.perform(delete(ITEM_URL, 9999, 300).header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isNotFound());
    }

    // ── Path param type mismatch ──────────────────────────────────────────────

    @Test
    void invalidPathParamType_returns400() throws Exception {
        mvc.perform(get("/landscape/abc/callouts").header("X-EGRC-UserId", USERNAME))
                .andExpect(status().isBadRequest());
    }
}
