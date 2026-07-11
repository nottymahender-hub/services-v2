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
 * Integration tests for {@link LandscapeAssmtDetailsController}.
 *
 * <h3>Seed data</h3>
 * <ul>
 *   <li>Dim 1 ("Alpha"): RISK_AREA JSON has "Cyber Risk"→["OR"] and "Conduct Risk"→["CR"],
 *       BIZ_UNIT_LVL=2, BIZ_UNITS="Tech,Ops", LOCATIONS="SG,HK"</li>
 *   <li>Dim 2 ("Beta"): RISK_AREA JSON has "Operational Risk"→["OR"], BIZ_UNIT_LVL=3,
 *       BIZ_UNITS=null, LOCATIONS="IN"</li>
 *   <li>Assmt 5 ("Q1-2024", status=Open, no UPDATE_DT_TM) → dim 1</li>
 *   <li>Assmt 6 ("Q2-2024", status=Draft) → dim 1</li>
 *   <li>Assmt 7 ("Q1-2024", status=Open, has UPDATE_DT_TM) → dim 2 (BIZ_UNIT_LVL=3)</li>
 *   <li>Detail rows for assmt 5: ids 101(OR,L4,BCM), 102(OR,L3,DTI), 103(OR,L2,Tech),
 *       110(CR,grp_l2) — ORDER BY RISK_AREA, LOCATION: CR(110), OR/HK(102), OR/IN(103), OR/SG(101)</li>
 *   <li>Detail rows for assmt 6 (dim 1, BIZ_UNIT_LVL=2): id 104 (L2), id 106 (category=loc,
 *       ORL_BU_NM_L2 set but must be ignored — bu must resolve to the literal "Group")</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LandscapeAssmtDetailsControllerTest {

    private static final String URL_TPL = "/landscape/{id}/assessments";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");

        // Dim 1: JSON RISK_AREA with two entries; Dim 2: single entry, null BIZ_UNITS, level 3
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '{"Cyber Risk":["OR"],"Conduct Risk":["CR"]}','Tech,Ops',2,'SG,HK','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(2,'CFG002','Beta',DATE '2024-01-01',1,
                    '{"Operational Risk":["OR"]}',NULL,3,'IN','seed')
                """);

        // Assmt 4 is the previous assessment of assmt 5 (linked via PREV_ASSMT_NUM).
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(4,1,'Q4-2023','Closed','seed')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,PREV_ASSMT_NUM,CREATED_BY) VALUES(5,1,'Q1-2024','Open',4,'seed')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(6,1,'Q2-2024','Draft','seed')");
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY,CREATE_DT_TM,UPDATE_DT_TM,UPDATED_BY)
                VALUES(7,2,'Q1-2024','Open','seed',TIMESTAMP '2024-01-01 00:00:00',TIMESTAMP '2024-06-01 10:00:00','editor')
                """);

        // Previous assessment (id 4) rows — matched by dimension key to derive prevAssmtFinalNRR:
        //   matches row 101 (OR,Tech,DTI,BCM,SG,L4): OVRLY set → 'High'
        //   matches row 102 (OR,Tech,DTI,-,HK,L3):  OVRLY null → falls back to CAL 'Med Low'
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,
                     LOCATION,category,CAL_NET_RISK_RTNG,OVRLY_NET_RISK_RTNG,CTRL_EFF_RTN,STATUS,CREATED_BY)
                VALUES(90,4,'OR','Tech','DTI','BCM','SG','L4','Low','High','Effective','Completed','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,
                     LOCATION,category,CAL_NET_RISK_RTNG,OVRLY_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(91,4,'OR','Tech','DTI',NULL,'HK','L3','Med Low',NULL,'Completed','seed')
                """);

        // assmt 5 detail rows — ORDER BY RISK_AREA, LOCATION: CR/SG(110), OR/HK(102), OR/IN(103), OR/SG(101)
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,
                     LOCATION,category,CAL_NET_RISK_RTNG,OVRLY_NET_RISK_RTNG,
                     RISK_RTNG_CHGE,COMMENTARY,STATUS,CREATED_BY)
                VALUES(101,5,'OR','Tech','DTI','BCM','SG','L4','Low','High',
                       'Improved','Commentary for 101','Open','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,
                     LOCATION,category,CAL_NET_RISK_RTNG,OVRLY_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(102,5,'OR','Tech','DTI',NULL,'HK','L3','Low',NULL,'Locked','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,
                     LOCATION,category,CAL_NET_RISK_RTNG,OVRLY_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(103,5,'OR','Tech',NULL,NULL,'IN','L2','Low',NULL,'Completed','seed')
                """);
        // Group-level category — location must resolve to 'Group', not the raw LOCATION value
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,
                     LOCATION,category,CAL_NET_RISK_RTNG,OVRLY_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(110,5,'CR','Risk Mgmt',NULL,NULL,'SG','grp_l2','Low',NULL,'Open','seed')
                """);
        // assmt 6 row — must not appear when querying id=5
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,
                     CAL_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(104,6,'OR','Tech','SG','L2','Low','Open','seed')
                """);
        // assmt 6, category='loc' — bu must resolve to "Group" regardless of BIZ_UNIT_LVL/ORL_BU_NM_L2
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,
                     CAL_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(106,6,'ZZ','ShouldBeIgnored','SG','loc','Low','Open','seed')
                """);
        // assmt 7 row (BIZ_UNIT_LVL=3 → bu resolves from ORL_BU_NM_L3)
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,LOCATION,category,
                     CAL_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(105,7,'OR','Tech','TechL3','IN','L3','Low','Open','seed')
                """);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Test
    void missingXUsername_returns401() throws Exception {
        mvc.perform(get(URL_TPL, 5))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.success", is(false)))
           .andExpect(jsonPath("$.message", containsString("X-EGRC-UserId")));
    }

    @Test
    void blankXUsername_returns401() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "  "))
           .andExpect(status().isUnauthorized());
    }

    // ── Path-parameter validation ─────────────────────────────────────────────

    @Test
    void nonNumericPathParam_returns400() throws Exception {
        mvc.perform(get("/landscape/abc/assessments").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("abc")));
    }

    // ── Unknown assessment id → 404 ───────────────────────────────────────────

    @Test
    void nonExistentAssmtId_returns404() throws Exception {
        mvc.perform(get(URL_TPL, 9999).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.success", is(false)))
           .andExpect(jsonPath("$.message", containsString("9999")));
    }

    // ── Top-level header fields ───────────────────────────────────────────────

    @Test
    void topLevelFields_arePopulatedFromAssmtAndDim() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.lndscpName", is("Alpha")))
           .andExpect(jsonPath("$.data.lndscpAssmtId", is(5)))
           .andExpect(jsonPath("$.data.lndscpAssmtPeriod", is("Q1-2024")))
           .andExpect(jsonPath("$.data.lndscpAssmtStatus", is("Open")));
    }

    @Test
    void lndscpLastRefreshed_fallsBackToCreateDtTm_whenUpdateDtTmNull() throws Exception {
        // assmt 5 has no UPDATE_DT_TM
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.lndscpLastRefreshed").exists());
    }

    @Test
    void lndscpLastRefreshed_usesUpdateDtTm_whenPresent() throws Exception {
        // assmt 7 has UPDATE_DT_TM = 2024-06-01T10:00:00
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.lndscpLastRefreshed", startsWith("2024-06-01T10:00:00")));
    }

    // ── dimensions.riskAreas ──────────────────────────────────────────────────

    @Test
    void dimensions_riskAreas_isAMap() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.riskAreas").isMap());
    }

    @Test
    void dimensions_riskAreas_containsExpectedKeys() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.riskAreas['Cyber Risk']").isArray())
           .andExpect(jsonPath("$.data.dimensions.riskAreas['Cyber Risk'][0]", is("OR")))
           .andExpect(jsonPath("$.data.dimensions.riskAreas['Conduct Risk']").isArray())
           .andExpect(jsonPath("$.data.dimensions.riskAreas['Conduct Risk'][0]", is("CR")));
    }

    @Test
    void dimensions_riskAreas_forDim2() throws Exception {
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.riskAreas['Operational Risk'][0]", is("OR")));
    }

    // ── dimensions.buDetails ─────────────────────────────────────────────────

    @Test
    void dimensions_buDetails_containsLvlAndBizUnits() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.buDetails.lvl", is(2)))
           .andExpect(jsonPath("$.data.dimensions.buDetails.bizUnits").isArray())
           .andExpect(jsonPath("$.data.dimensions.buDetails.bizUnits", hasSize(2)))
           .andExpect(jsonPath("$.data.dimensions.buDetails.bizUnits", hasItems("Tech", "Ops")));
    }

    @Test
    void dimensions_buDetails_bizUnits_omittedWhenDimHasNullBizUnits() throws Exception {
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.buDetails.bizUnits").doesNotExist());
    }

    @Test
    void dimensions_buDetails_lvlStillPresentWhenBizUnitsNull() throws Exception {
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.buDetails.lvl", is(3)));
    }

    // ── dimensions.locations ─────────────────────────────────────────────────

    @Test
    void dimensions_locations_isArray_withAllValues() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions.locations").isArray())
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

    // ── assessments array ─────────────────────────────────────────────────────

    @Test
    void assessments_returnsOnlyRowsForThatId() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments", hasSize(4)));
    }

    @Test
    void assessmentItem_id_orderedByRiskAreaThenLocation() throws Exception {
        // ORDER BY RISK_AREA, LOCATION: CR/SG(110), OR/HK(102), OR/IN(103), OR/SG(101)
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[0].id", is(110)))
           .andExpect(jsonPath("$.data.assessments[1].id", is(102)))
           .andExpect(jsonPath("$.data.assessments[2].id", is(103)))
           .andExpect(jsonPath("$.data.assessments[3].id", is(101)));
    }

    @Test
    void assessmentItem_allFieldsPresent() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[1].id").exists())
           .andExpect(jsonPath("$.data.assessments[1].bu").exists())
           .andExpect(jsonPath("$.data.assessments[1].location").exists())
           .andExpect(jsonPath("$.data.assessments[1].category").exists())
           .andExpect(jsonPath("$.data.assessments[1].status").exists())
           .andExpect(jsonPath("$.data.assessments[1].riskArea").exists())
           .andExpect(jsonPath("$.data.assessments[1].nrrCalculated").exists())
           .andExpect(jsonPath("$.data.assessments[1].nrrOverlaid").exists());
    }

    @Test
    void assessmentItem_riskArea_matchesDetailColumn() throws Exception {
        // assessments[0] = id 110 (CR); assessments[1] = id 102 (OR)
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[0].riskArea", is("CR")))
           .andExpect(jsonPath("$.data.assessments[1].riskArea", is("OR")));
    }

    @Test
    void assessmentItem_doesNotContainDimFields() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[0].bizUnits").doesNotExist())
           .andExpect(jsonPath("$.data.assessments[0].locations").doesNotExist())
           .andExpect(jsonPath("$.data.assessments[0].bizUnitLvl").doesNotExist())
           .andExpect(jsonPath("$.data.assessments[0].buDetails").doesNotExist());
    }

    // ── bu derivation (by BIZ_UNIT_LVL) ───────────────────────────────────────

    @Test
    void bu_whenLvl2_usesOrlBuNmL2() throws Exception {
        // dim 1 (assmt 5) has BIZ_UNIT_LVL=2; assessments[2] = id 103 (L2=Tech)
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[2].bu", is("Tech")));
    }

    @Test
    void bu_whenLvl3_usesOrlBuNmL3() throws Exception {
        // dim 2 (assmt 7) has BIZ_UNIT_LVL=3; assessments[0] = id 105 (L3=TechL3)
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[0].bu", is("TechL3")));
    }

    @Test
    void bu_whenCategoryIsLoc_returnsLiteralGroup_regardlessOfBizUnitLvl() throws Exception {
        // assmt 6 → dim 1 (BIZ_UNIT_LVL=2); row id=106 has category='loc' and ORL_BU_NM_L2 set,
        // but 'loc' must take priority over the BIZ_UNIT_LVL-based lookup.
        mvc.perform(get(URL_TPL, 6).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==106)].bu", hasItem("Group")));
    }

    // ── location derivation (by category) ─────────────────────────────────────

    @Test
    void location_whenCategoryIsL4_returnsRawLocation() throws Exception {
        // assessments[3] = id 101 (category=L4, LOCATION=SG)
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[3].location", is("SG")));
    }

    @Test
    void location_whenCategoryIsGroupLevel_returnsLiteralGroup() throws Exception {
        // assessments[0] = id 110 (category=grp_l2, LOCATION=SG) → must resolve to "Group"
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[0].location", is("Group")));
    }

    // ── nrr fields ─────────────────────────────────────────────────────────────

    @Test
    void nrrCalculated_returnsCalNetRiskRtngDirectly() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[3].nrrCalculated", is("Low")));
    }

    @Test
    void nrr_returnsOvrlyNetRiskRtngDirectly() throws Exception {
        // assessments[3] = id 101 (OVRLY_NET_RISK_RTNG='High')
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[3].nrr", is("High")));
    }

    @Test
    void nrrOverlaid_isY_whenOvrlyNetRiskRtngPresent() throws Exception {
        // assessments[3] = id 101 has OVRLY_NET_RISK_RTNG='High'
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[3].nrrOverlaid", is("Y")));
    }

    @Test
    void nrrOverlaid_isN_whenOvrlyNetRiskRtngNull() throws Exception {
        // assessments[1] = id 102 has OVRLY_NET_RISK_RTNG=null
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[1].nrrOverlaid", is("N")));
    }

    @Test
    void riskRatingChangeAndSummary_arePassedThrough() throws Exception {
        // assessments[3] = id 101
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[3].riskRatingChange", is("Improved")))
           .andExpect(jsonPath("$.data.assessments[3].summary", is("Commentary for 101")));
    }

    // ── prevAssmtFinalNRR (derived from PREV_ASSMT_NUM assessment) ─────────────

    @Test
    void prevAssmtFinalNRR_usesOvrlyOfMatchedPrevRow_whenOverlaid() throws Exception {
        // assessments[3] = id 101 → matched prev row 90 has OVRLY='High'
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[3].prevAssmtFinalNRR", is("High")));
    }

    @Test
    void prevAssmtFinalNRR_fallsBackToCalOfMatchedPrevRow_whenNotOverlaid() throws Exception {
        // assessments[1] = id 102 → matched prev row 91 has OVRLY=null, CAL='Med Low'
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[1].prevAssmtFinalNRR", is("Med Low")));
    }

    @Test
    void prevAssmtFinalNRR_null_whenNoMatchingPrevRow() throws Exception {
        // assessments[0] = id 110 (CR row) has no dimension match in the previous assessment
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[0].prevAssmtFinalNRR").doesNotExist());
    }

    @Test
    void prevAssmtFinalNRR_null_whenAssessmentHasNoPreviousLink() throws Exception {
        // assmt 6 has PREV_ASSMT_NUM=null → every row's prevAssmtFinalNRR is absent
        mvc.perform(get(URL_TPL, 6).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[*].prevAssmtFinalNRR", hasSize(0)));
    }

    // ── message content ────────────────────────────────────────────────────────

    @Test
    void messageContainsCount_whenDataFound() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message", containsString("4")));
    }
}
