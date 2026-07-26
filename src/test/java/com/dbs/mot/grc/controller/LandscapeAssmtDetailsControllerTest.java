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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private static final String URL_TPL = "/landscape/assessments/{id}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        jdbc.execute("DELETE FROM fact_orl");

        // Dim 1: grouped RISK_AREA — 'OR' in group "Cyber" (clusters C1,C2); 'CR' in group
        // "Conduct" (cluster C3). Detail rows key on the risk area name (OR / CR).
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '[{"groupName":"Cyber","isGroup":true,"riskAreas":[{"riskArea":"OR","riskClusters":["C1","C2"]}]},{"groupName":"Conduct","isGroup":false,"riskAreas":[{"riskArea":"CR","riskClusters":["C3"]}]}]',
                    'Tech,Ops',2,'SG,HK','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(2,'CFG002','Beta',DATE '2024-01-01',1,
                    '[{"groupName":"Ops","isGroup":false,"riskAreas":[{"riskArea":"OR","riskClusters":["X1"]}]}]',
                    NULL,3,'IN','seed')
                """);

        // Assmt 4 is the previous assessment of assmt 5 (linked via PREV_ASSMT_NUM).
        // biz_dt sets each assessment's fact_orl business date: assmt 4 → 2024-06-01, assmt 5 → 2024-07-01.
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,CREATED_BY,CREATE_DT_TM) VALUES(4,1,'Q4-2023',DATE '2024-06-01','Closed','seed',TIMESTAMP '2024-06-01 00:00:00')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,PREV_ASSMT_NUM,CREATED_BY,CREATE_DT_TM) VALUES(5,1,'Q1-2024',DATE '2024-07-01','Open',4,'seed',TIMESTAMP '2024-07-01 00:00:00')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,CREATED_BY,CREATE_DT_TM) VALUES(6,1,'Q2-2024',DATE '2024-07-01','Draft','seed',TIMESTAMP '2024-07-01 00:00:00')");
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,CREATED_BY,CREATE_DT_TM,UPDATE_DT_TM,UPDATED_BY)
                VALUES(7,2,'Q1-2024',DATE '2024-06-01','Open','seed',TIMESTAMP '2024-01-01 00:00:00',TIMESTAMP '2024-06-01 10:00:00','editor')
                """);

        // ── fact_orl snapshots ────────────────────────────────────────────────
        // Current month (biz_dt 2024-07-01, for assmt 5): computed values per dimension.
        insertFact("2024-07-01", "OR", "Tech", "DTI", "BCM", "SG", "Low", "Improved", "Commentary for 101");
        insertFact("2024-07-01", "OR", "Tech", "DTI", null, "HK", "Low", "Stable", "Commentary for 102");
        insertFact("2024-07-01", "OR", "Tech", null, null, "IN", "Low", "Stable", null);
        insertFact("2024-07-01", "CR", "Risk Mgmt", null, null, "SG", "Low", "Stable", null);
        // Previous month (biz_dt 2024-06-01, for assmt 4): row 91's dimension falls back to CAL.
        insertFact("2024-06-01", "OR", "Tech", "DTI", null, "HK", "Med Low", "Deteriorated", "June commentary");

        // Previous assessment (id 4) thin rows — matched by dimension to derive prevAssmtFinalNRR:
        //   matches row 101 (OR,Tech,DTI,BCM,SG): OVRLY set → 'High'
        //   matches row 102 (OR,Tech,DTI,-,HK):  OVRLY null → falls back to prev fact CAL 'Med Low'
        insertDetail(90, 4, "OR", "Tech", "DTI", "BCM", "SG", "L4", "High", "Completed");
        insertDetail(91, 4, "OR", "Tech", "DTI", null, "HK", "L3", null, "Completed");

        // assmt 5 detail rows — response is ordered by id asc: 101, 102, 103, 110
        insertDetail(101, 5, "OR", "Tech", "DTI", "BCM", "SG", "L4", "High", "Open");
        insertDetail(102, 5, "OR", "Tech", "DTI", null, "HK", "L3", null, "Locked");
        insertDetail(103, 5, "OR", "Tech", null, null, "IN", "L2", null, "Completed");
        // Group-level category — location must resolve to 'Group', not the raw LOCATION value
        insertDetail(110, 5, "CR", "Risk Mgmt", null, null, "SG", "grp_l2", null, "Open");
        // assmt 6 rows — must not appear when querying id=5
        insertDetail(104, 6, "OR", "Tech", null, null, "SG", "L2", null, "Open");
        // assmt 6, category='loc' — bu must resolve to "Group" regardless of BIZ_UNIT_LVL/ORL_BU_NM_L2
        insertDetail(106, 6, "ZZ", "ShouldBeIgnored", null, null, "SG", "loc", null, "Open");
        // assmt 7 row (BIZ_UNIT_LVL=3 → bu resolves from ORL_BU_NM_L3)
        insertDetail(105, 7, "OR", "Tech", "TechL3", null, "IN", "L3", null, "Open");
    }

    /** Inserts a thin orl_lndscp_assmt_details row (empty BU/location dimensions stored as ''). */
    private void insertDetail(long id, long assmtId, String riskArea, String l2, String l3, String l4,
                              String loc, String category, String ovrly, String status) {
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,
                     LOCATION,category,OVRLY_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,'seed')
                """.formatted(id, assmtId, q(riskArea), qDim(l2), qDim(l3), qDim(l4), qDim(loc),
                q(category), q(ovrly), q(status)));
    }

    /** Inserts a fact_orl snapshot row (empty dimensions stored as '', matching details). */
    private void insertFact(String bizDt, String riskArea, String l2, String l3, String l4,
                            String loc, String cal, String rtngChge, String commentary) {
        jdbc.execute("""
                INSERT INTO fact_orl
                    (biz_dt,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,LOCATION,category,
                     CAL_NET_RISK_RTNG,RISK_RTNG_CHGE,CTRL_EFF_RTN,COMMENTARY)
                VALUES(DATE %s,%s,%s,%s,%s,%s,'L2',%s,%s,'Satisfactory to Good',%s)
                """.formatted(q(bizDt), q(riskArea), qDim(l2), qDim(l3), qDim(l4), qDim(loc),
                q(cal), q(rtngChge), q(commentary)));
    }

    /** SQL literal: quoted value, or NULL. */
    private String q(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    /** SQL literal for a NOT NULL dimension column: null becomes the empty string ''. */
    private String qDim(String value) {
        return "'" + (value == null ? "" : value.replace("'", "''")) + "'";
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
        mvc.perform(get("/landscape/assessments/abc").header("X-EGRC-UserId", "tester"))
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

    // ── The /{id}/assessments path is GET-only ────────────────────────────────

    @Test
    void postToAssessmentsPath_returns405() throws Exception {
        // The path template only maps GET (assessment details); POST is not supported.
        mvc.perform(post(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isMethodNotAllowed());
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
    void lndscpLastRefreshed_isLatestFactBizDt() throws Exception {
        // Latest biz_dt across fact_orl is 2024-07-01 (a 2024-06-01 row also exists).
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.lndscpLastRefreshed", is("2024-07-01")));
    }

    @Test
    void lndscpLastRefreshed_null_whenNoFacts() throws Exception {
        jdbc.execute("DELETE FROM fact_orl");
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.lndscpLastRefreshed").value(nullValue()));
    }

    @Test
    void lndscpLastModifiedOn_fallsBackToCreateDtTm_whenUpdateNull() throws Exception {
        // assmt 5 has no UPDATE_DT_TM → falls back to CREATE_DT_TM / CREATED_BY.
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.lndscpLastModifiedOn", startsWith("2024-07-01T00:00:00")))
           .andExpect(jsonPath("$.data.lndscpLastModifiedBy", is("seed")));
    }

    @Test
    void lndscpLastModifiedOn_usesUpdateFields_whenPresent() throws Exception {
        // assmt 7 has UPDATE_DT_TM = 2024-06-01T10:00:00 and UPDATED_BY = 'editor'.
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.lndscpLastModifiedOn", startsWith("2024-06-01T10:00:00")))
           .andExpect(jsonPath("$.data.lndscpLastModifiedBy", is("editor")));
    }

    // ── embedded dimensions + callouts ─────────────────────────────────────────

    @Test
    void assessmentsResponse_embedsDimensionsAndCallouts() throws Exception {
        // Dimensions and callouts are now returned inline with the assessment details.
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.dimensions").exists())
           .andExpect(jsonPath("$.data.dimensions.riskAreas").exists())
           .andExpect(jsonPath("$.data.callouts").exists())
           .andExpect(jsonPath("$.data.callouts.callouts").isArray());
    }

    // ── assessments array ─────────────────────────────────────────────────────

    @Test
    void assessments_returnsOnlyRowsForThatId() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments", hasSize(4)));
    }

    @Test
    void assessments_containAllExpectedRows() throws Exception {
        // No ordering is applied; assert membership regardless of order.
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[*].id", containsInAnyOrder(101, 102, 103, 110)));
    }

    @Test
    void assessmentItem_allFieldsPresent() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[0].id").exists())
           .andExpect(jsonPath("$.data.assessments[0].bu").exists())
           .andExpect(jsonPath("$.data.assessments[0].location").exists())
           .andExpect(jsonPath("$.data.assessments[0].category").exists())
           .andExpect(jsonPath("$.data.assessments[0].status").exists())
           .andExpect(jsonPath("$.data.assessments[0].riskArea").exists())
           .andExpect(jsonPath("$.data.assessments[0].groupName").exists())
           .andExpect(jsonPath("$.data.assessments[0].riskClusters").exists())
           .andExpect(jsonPath("$.data.assessments[0].commentry").exists())
           .andExpect(jsonPath("$.data.assessments[0].nrrCalculated").exists())
           .andExpect(jsonPath("$.data.assessments[0].ctrlEffRtn").exists())
           .andExpect(jsonPath("$.data.assessments[0].nrrOverlaid").exists());
    }

    @Test
    void assessmentItem_groupNameAndRiskClusters_resolvedFromParentDim() throws Exception {
        // id 101 (OR) → group "Cyber", clusters [C1,C2]; id 110 (CR) → group "Conduct", clusters [C3].
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].groupName", hasItem("Cyber")))
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].riskClusters[*]", hasItems("C1", "C2")))
           .andExpect(jsonPath("$.data.assessments[?(@.id==110)].groupName", hasItem("Conduct")))
           .andExpect(jsonPath("$.data.assessments[?(@.id==110)].riskClusters[*]", hasItem("C3")));
    }

    @Test
    void assessmentItem_riskArea_matchesDetailColumn() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].riskArea", hasItem("OR")))
           .andExpect(jsonPath("$.data.assessments[?(@.id==110)].riskArea", hasItem("CR")));
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
        // dim 1 (assmt 5) has BIZ_UNIT_LVL=2; id 103 (L2=Tech)
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==103)].bu", hasItem("Tech")));
    }

    @Test
    void bu_whenLvl3_usesOrlBuNmL3() throws Exception {
        // dim 2 (assmt 7) has BIZ_UNIT_LVL=3; id 105 (L3=TechL3)
        mvc.perform(get(URL_TPL, 7).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==105)].bu", hasItem("TechL3")));
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
        // id 101 (category=L4, LOCATION=SG)
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].location", hasItem("SG")));
    }

    @Test
    void location_whenCategoryIsGroupLevel_returnsLiteralGroup() throws Exception {
        // id 110 (category=grp_l2, LOCATION=SG) → must resolve to "Group"
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==110)].location", hasItem("Group")));
    }

    // ── nrr fields ─────────────────────────────────────────────────────────────

    @Test
    void nrrCalculated_returnsDisplayForm() throws Exception {
        // id 101 (fact CAL='Low') → display "Low Risk"
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].nrrCalculated", hasItem("Low Risk")));
    }

    @Test
    void nrr_returnsOvrlyNetRiskRtngDisplayForm() throws Exception {
        // id 101 (OVRLY='High') → display "High Risk"
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].nrr", hasItem("High Risk")));
    }

    @Test
    void ctrlEffRtn_returnsFactValue() throws Exception {
        // id 101 matches a current fact with CTRL_EFF_RTN='Satisfactory to Good'
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].ctrlEffRtn", hasItem("Satisfactory to Good")));
    }

    @Test
    void nrrOverlaid_isY_whenOvrlyNetRiskRtngPresent() throws Exception {
        // id 101 has OVRLY_NET_RISK_RTNG='High'
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].nrrOverlaid", hasItem("Y")));
    }

    @Test
    void nrrOverlaid_isN_whenOvrlyNetRiskRtngNull() throws Exception {
        // id 102 has OVRLY_NET_RISK_RTNG=null
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==102)].nrrOverlaid", hasItem("N")));
    }

    @Test
    void riskRatingChangeAndCommentry_arePassedThrough() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].riskRatingChange", hasItem("Improved")))
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].commentry", hasItem("Commentary for 101")));
    }

    // ── prevAssmtFinalNRR (derived from PREV_ASSMT_NUM assessment) ─────────────

    @Test
    void prevAssmtFinalNRR_usesOvrlyOfMatchedPrevRow_whenOverlaid() throws Exception {
        // id 101 → matched prev row 90 has OVRLY='High' → display "High Risk"
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==101)].prevAssmtFinalNRR", hasItem("High Risk")));
    }

    @Test
    void prevAssmtFinalNRR_fallsBackToCalOfMatchedPrevRow_whenNotOverlaid() throws Exception {
        // id 102 → matched prev row 91 has OVRLY=null, CAL='Med Low' → display "Medium-Low Risk"
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==102)].prevAssmtFinalNRR", hasItem("Medium-Low Risk")));
    }

    @Test
    void prevAssmtFinalNRR_null_whenNoMatchingPrevRow() throws Exception {
        // id 110 (CR row) has no dimension match in the previous assessment → null value present.
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[?(@.id==110)].prevAssmtFinalNRR", hasItem(nullValue())));
    }

    @Test
    void prevAssmtFinalNRR_null_whenAssessmentHasNoPreviousLink() throws Exception {
        // assmt 6 has PREV_ASSMT_NUM=null → every row's prevAssmtFinalNRR is present but null
        mvc.perform(get(URL_TPL, 6).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.assessments[*].prevAssmtFinalNRR", everyItem(nullValue())));
    }

    // ── message content ────────────────────────────────────────────────────────

    @Test
    void messageContainsCount_whenDataFound() throws Exception {
        mvc.perform(get(URL_TPL, 5).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message", containsString("4")));
    }
}
