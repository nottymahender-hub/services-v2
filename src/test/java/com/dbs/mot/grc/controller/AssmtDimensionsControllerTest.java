package com.dbs.mot.grc.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code GET /landscape/{lndscpAssmtId}/dimensions}.
 *
 * <h3>Seed data</h3>
 * <ul>
 *   <li>Dim 1 ("Alpha"): riskAreas {@code {"Cyber Risk":["OR"],"Conduct Risk":["CR"]}},
 *       BIZ_UNIT_LVL=2, BIZ_UNITS="Tech,Ops", LOCATIONS="SG,HK"; assessment 5 → dim 1.</li>
 *   <li>Dim 2 ("Beta"): riskAreas {@code {"Operational Risk":["OR"]}}, BIZ_UNIT_LVL=3,
 *       BIZ_UNITS=null, LOCATIONS="IN"; assessment 7 → dim 2.</li>
 * </ul>
 * The endpoint reads only the assessment header (landscape FK) + its dim, so no detail
 * rows are seeded.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssmtDimensionsControllerTest {

    private static final String URL_TPL = "/landscape/assessments/{id}";

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
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '[{"groupName":"IT","isGroup":true,"riskAreas":[{"riskArea":"Cyber Risk","riskClusters":["OR"]},{"riskArea":"Conduct Risk","riskClusters":["CR"]}]}]',
                    'Tech,Ops',2,'SG,HK','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(2,'CFG002','Beta',DATE '2024-01-01',1,
                    '[{"groupName":"Ops","isGroup":false,"riskAreas":[{"riskArea":"Operational Risk","riskClusters":["OR"]}]}]',
                    NULL,3,'IN','seed')
                """);

        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,CREATED_BY) VALUES(5,1,'Q1-2024',DATE '2024-01-31','Open','seed')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,CREATED_BY) VALUES(7,2,'Q1-2024',DATE '2024-01-31','Open','seed')");
    }

    // ── Authentication / validation ────────────────────────────────────────────

    @Test
    void missingUserId_returns401() throws Exception {
        mvc.perform(get(URL_TPL, 5))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.message", containsString("X-EGRC-UserId")));
    }

    @Test
    void nonNumericPathParam_returns400() throws Exception {
        mvc.perform(get("/landscape/assessments/abc").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("abc")));
    }

    @Test
    void unknownAssessment_returns404() throws Exception {
        mvc.perform(get(URL_TPL, 9999).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.success", is(false)))
           .andExpect(jsonPath("$.message", containsString("9999")));
    }

    // ── riskAreas ──────────────────────────────────────────────────────────────

    @Test
    void dimensions_riskAreas_returnFlatMapAndUniqueClusters() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.riskAreas").isMap())
           .andExpect(jsonPath("$.data.dimensions.riskAreas['Cyber Risk']", contains("OR")))
           .andExpect(jsonPath("$.data.dimensions.riskAreas['Conduct Risk']", contains("CR")))
           .andExpect(jsonPath("$.data.dimensions.riskClusters", containsInAnyOrder("OR", "CR")));
    }

    @Test
    void dimensions_riskAreas_forDim2() throws Exception {
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.riskAreas['Operational Risk']", contains("OR")))
           .andExpect(jsonPath("$.data.dimensions.riskClusters", contains("OR")));
    }

    // ── buDetails ──────────────────────────────────────────────────────────────

    @Test
    void dimensions_buDetails_containsLvlAndBizUnits() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.buDetails.lvl", is(2)))
           .andExpect(jsonPath("$.data.dimensions.buDetails.bizUnits", hasSize(2)))
           .andExpect(jsonPath("$.data.dimensions.buDetails.bizUnits", hasItems("Tech", "Ops")));
    }

    @Test
    void dimensions_buDetails_bizUnits_nullWhenDimHasNullBizUnits() throws Exception {
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.buDetails.lvl", is(3)))
           .andExpect(jsonPath("$.data.dimensions.buDetails.bizUnits").value(nullValue()));
    }

    // ── locations ──────────────────────────────────────────────────────────────

    @Test
    void dimensions_locations_isArray_withAllValues() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.locations", hasSize(2)))
           .andExpect(jsonPath("$.data.dimensions.locations", hasItems("SG", "HK")));
    }

    @Test
    void dimensions_locations_singleValue_returnedAsArray() throws Exception {
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.locations", hasSize(1)))
           .andExpect(jsonPath("$.data.dimensions.locations[0]", is("IN")));
    }

    // ── message ──────────────────────────────────────────────────────────────

    @Test
    void dimensions_successMessageIncludesAssmtId() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.success", is(true)))
           .andExpect(jsonPath("$.message", containsString("5")));
    }
}
