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
 * Integration tests for {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}} (drill-down).
 *
 * <p>Row-level values come from {@code fact_orl}; GRC metrics are assembled from the per-module
 * fact tables ({@code rcsa/inc/ina/kri _fact_orl}) at the block's business date (current/previous)
 * or each module's own latest date (live).
 *
 * <h3>Seed data (dimension: AML Sanctions / CBG / SG)</h3>
 * <ul>
 *   <li>Assmt 10 ("June 2026", 2026-06-30) — previous; detail 200 (OVRLY='Med High').</li>
 *   <li>Assmt 11 ("July 2026", 2026-07-15, PREV=10) — current; detail 300 (OVRLY='High').</li>
 *   <li>Detail 301 (grp_l2, no matching facts/modules), 302 (loc) under assmt 11.</li>
 *   <li>Assmt 12 ("July 2026", dim 2, no PREV) with detail 310.</li>
 *   <li>Module rows for the dimension: INC @06-30/07-15/07-31, KRI @07-15, RCSA @07-15.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssmtDetailByIdControllerTest {

    private static final String URL_TPL = "/landscape/assessment/{assmtId}/assessmentDetail/{detailId}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        jdbc.execute("DELETE FROM fact_orl");
        jdbc.execute("DELETE FROM inc_fact_orl");
        jdbc.execute("DELETE FROM kri_fact_orl");
        jdbc.execute("DELETE FROM rcsa_fact_orl");

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '[{"groupName":"Financial Crime","isGroup":false,"riskAreas":[{"riskArea":"AML Sanctions","riskClusters":["OR"]}]}]','CBG',2,'SG','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(2,'CFG002','Beta',DATE '2024-01-01',1,
                    '[{"groupName":"Financial Crime","isGroup":false,"riskAreas":[{"riskArea":"AML Sanctions","riskClusters":["OR"]}]}]','CBG',2,'SG','seed')
                """);

        // biz_dt is the business date every fact/module lookup matches on; CREATE_DT_TM is audit only.
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,CREATED_BY,CREATE_DT_TM) VALUES(10,1,'June 2026',DATE '2026-06-30','Closed','seed',TIMESTAMP '2026-06-30 00:00:00')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,PREV_ASSMT_NUM,CREATED_BY,CREATE_DT_TM) VALUES(11,1,'July 2026',DATE '2026-07-15','Open',10,'seed',TIMESTAMP '2026-07-15 00:00:00')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,CREATED_BY,CREATE_DT_TM) VALUES(12,2,'July 2026',DATE '2026-07-15','Open','seed',TIMESTAMP '2026-07-15 00:00:00')");

        // fact_orl (row-level values) for (AML Sanctions, CBG, -, -, SG).
        insertFact("2026-06-30", "Low", "Attention Needed To Satisfactory", "June commentary"); // prev
        insertFact("2026-07-15", "Low", "Good", "July commentary");                             // current
        insertFact("2026-07-31", "Med Low", "Satisfactory to Good", "Live commentary");         // live

        // Per-module rows for the same dimension.
        insertIncFact("2026-06-30", "Low", 5);
        insertIncFact("2026-07-15", "High", 7);
        insertIncFact("2026-07-31", "Med Low", 9);
        insertKriFact("2026-07-15", "High", 4, 2, 1);
        insertRcsaFact("2026-07-15", "High", 3);

        insertDetail(200, 10, "CBG", "SG", "L2", "Med High", null, "Completed", null, null);
        insertDetail(300, 11, "CBG", "SG", "L2", "High", "Overlaid due to audit findings",
                "Open", "Revised July commentary", "user1");
        insertDetail(301, 11, "CBG", null, "grp_l2", null, null, "Open", null, null);
        insertDetailNoBu(302, 11, "SG", "loc", "Open");
        insertDetail(310, 12, "CBG", "SG", "L2", null, null, "Open", null, null);

        // Commentary-revision audit columns, stored UTC (09:00 → SGT 17:00 for the assertion below).
        // UPDATE_DT_TM is DB-managed (bumped on any UPDATE); re-assert it so the lastModifiedOn
        // assertion stays deterministic.
        jdbc.execute("UPDATE orl_lndscp_assmt_details "
                + "SET COMMENTARY_REVISED_BY='reviser1',"
                + "    COMMENTARY_REVISED_AT=TIMESTAMP '2026-07-05 09:00:00',"
                + "    UPDATE_DT_TM=TIMESTAMP '2026-07-05 09:00:00' WHERE id=300");
    }

    private void insertDetail(long id, long assmtId, String l2, String loc, String category,
                              String ovrly, String ovrlyJstfkn, String status,
                              String revisedCommentary, String updatedBy) {
        String updateDtTm = updatedBy != null ? "TIMESTAMP '2026-07-05 09:00:00'" : "NULL";
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,
                     OVRLY_NET_RISK_RTNG,OVRLY_JSTFKN,REVISED_COMMENTARY,STATUS,CREATED_BY,UPDATE_DT_TM,UPDATED_BY)
                VALUES(%d,%d,'AML Sanctions',%s,%s,%s,%s,%s,%s,%s,'seed',%s,%s)
                """.formatted(id, assmtId, qDim(l2), qDim(loc), q(category), q(ovrly), q(ovrlyJstfkn),
                q(revisedCommentary), q(status), updateDtTm, q(updatedBy)));
    }

    private void insertDetailNoBu(long id, long assmtId, String loc, String category, String status) {
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,LOCATION,category,STATUS,CREATED_BY)
                VALUES(%d,%d,'AML Sanctions',%s,%s,%s,'seed')
                """.formatted(id, assmtId, qDim(loc), q(category), q(status)));
    }

    private void insertFact(String bizDt, String cal, String ctrl, String commentary) {
        jdbc.execute("""
                INSERT INTO fact_orl
                    (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,CAL_NET_RISK_RTNG,CTRL_EFF_RTN,COMMENTARY)
                VALUES(DATE %s,'AML Sanctions','CBG','SG','L2',%s,%s,%s)
                """.formatted(q(bizDt), q(cal), q(ctrl), q(commentary)));
    }

    private void insertIncFact(String bizDt, String nrr, int sinp) {
        jdbc.execute("""
                INSERT INTO inc_fact_orl
                    (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RATING,inc_is_sinp_count_l3m_mtd)
                VALUES(DATE %s,'AML Sanctions','CBG','SG',%s,%d)
                """.formatted(q(bizDt), q(nrr), sinp));
    }

    private void insertKriFact(String bizDt, String nrr, int active, int red, int green) {
        jdbc.execute("""
                INSERT INTO kri_fact_orl
                    (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RATING,
                     KRI_ACTIVE_CNT,KRI_RED_CNT,KRI_GREEN_CNT)
                VALUES(DATE %s,'AML Sanctions','CBG','SG',%s,%d,%d,%d)
                """.formatted(q(bizDt), q(nrr), active, red, green));
    }

    private void insertRcsaFact(String bizDt, String nrr, int highRisk) {
        jdbc.execute("""
                INSERT INTO rcsa_fact_orl
                    (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RATING,combined_count_high_risk)
                VALUES(DATE %s,'AML Sanctions','CBG','SG',%s,%d)
                """.formatted(q(bizDt), q(nrr), highRisk));
    }

    private String q(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    private String qDim(String value) {
        return "'" + (value == null ? "" : value.replace("'", "''")) + "'";
    }

    // ── Authentication / not-found ──────────────────────────────────────────────

    @Test
    void missingUserId_returns401() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.message", containsString("X-EGRC-UserId")));
    }

    @Test
    void unknownAssessment_returns404() throws Exception {
        mvc.perform(get(URL_TPL, 9999, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", containsString("9999")));
    }

    @Test
    void detail_locatedByIdRegardlessOfPathAssessment_returns200() throws Exception {
        // The detail id is the primary key, so the row is located by id alone (detail 310
        // belongs to assessment 12 but is still returned here under the path assessment 11).
        mvc.perform(get(URL_TPL, 11, 310).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.id", is(310)));
    }

    @Test
    void nonExistentDetail_returns404() throws Exception {
        mvc.perform(get(URL_TPL, 11, 99999).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", containsString("99999")));
    }

    @Test
    void nonNumericDetailId_returns400() throws Exception {
        mvc.perform(get("/landscape/assessment/11/assessmentDetail/abc").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isBadRequest());
    }

    // ── Flat fields + landscapeId ─────────────────────────────────────────────────

    @Test
    void detail_flatFields_mappedFromRow() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.id", is(300)))
           .andExpect(jsonPath("$.data.landscapeAssessmentId", is(11)))
           .andExpect(jsonPath("$.data.landscapeId", is(1)))
           .andExpect(jsonPath("$.data.riskArea", is("AML Sanctions")))
           .andExpect(jsonPath("$.data.bu", is("CBG")))
           .andExpect(jsonPath("$.data.location", is("SG")))
           .andExpect(jsonPath("$.data.status", is("Open")))
           // UPDATE_DT_TM is stored UTC (09:00) and surfaced in SGT (+8h → 17:00).
           .andExpect(jsonPath("$.data.lastModifiedOn", startsWith("2026-07-05T17:00:00")))
           // Commentary-revision audit: who + when (UTC 09:00 → SGT 17:00).
           .andExpect(jsonPath("$.data.commentaryRevisedBy", is("reviser1")))
           .andExpect(jsonPath("$.data.commentaryRevisedAt", startsWith("2026-07-05T17:00:00")))
           .andExpect(jsonPath("$.data.lastModifiedBy", is("user1")))
           .andExpect(jsonPath("$.data.lastRefreshed", is("2026-07-31")))
           .andExpect(jsonPath("$.data.category", is("L2")))
           // Overlay fields belong to the per-month blocks, not the top level of the response.
           .andExpect(jsonPath("$.data.nrrOverlaid").doesNotExist())
           .andExpect(jsonPath("$.data.overlayJustfkn").doesNotExist());
    }

    // ── currentMonthNRRDetails ────────────────────────────────────────────────────

    @Test
    void detail_currentMonthNRRDetails_fromFactAndOverlay() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrCalculated", is("Low")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrr", is("High")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrOverlaid", is("Y")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.overlayJstfkn", is("Overlaid due to audit findings")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.ctrlEffRtn", is("Good")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.assmtPeriod", is("July 2026")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.commentry", is("July commentary")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.revisedCommentary", is("Revised July commentary")))
           // Commentary-revision audit for this month's own row (seeded in setUp for detail 300).
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.commentaryRevisedBy", is("reviser1")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.commentaryRevisedAt", startsWith("2026-07-05T17:00:00")))
           // Overall change is computed fresh: previous assessment's final rating for this dimension
           // (detail 200's overlay 'Med High') vs. this row's effective rating (overlay 'High') →
           // more severe → Deteriorated. Never read from a stored column.
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.riskRatingChange", is("Deteriorated")));
    }

    @Test
    void detail_currentMonthGrcMetrics_assembledFromModuleTables() throws Exception {
        String grc = "$.data.currentMonthNRRDetails.grcMetrics";
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath(grc).isMap())
           // INC block: nrr (DB value) + module-level change, both computed by comparing this
           // month's INC fact (High) against the previous assessment's INC fact (Low) → Deteriorated.
           .andExpect(jsonPath(grc + ".INC.nrr", is("High")))
           .andExpect(jsonPath(grc + ".INC.riskRatingChge", is("Deteriorated")))
           // Metrics are a list of {name,value,riskRatingChge}; look them up by name.
           // sinp count 7 (this month) vs. 5 (previous month) → Increased.
           .andExpect(jsonPath(grc + ".INC.metrics[?(@.name=='inc_is_sinp_count_l3m_mtd')].value", hasItem(7)))
           .andExpect(jsonPath(grc + ".INC.metrics[?(@.name=='inc_is_sinp_count_l3m_mtd')].riskRatingChge", hasItem("Increased")))
           // RCSA/KRI have no previous-month row, so their module change is N.A even though they
           // have a value this month.
           .andExpect(jsonPath(grc + ".RCSA.metrics[?(@.name=='combined_count_high_risk')].value", hasItem(3)))
           .andExpect(jsonPath(grc + ".RCSA.riskRatingChge", is("N.A")))
           .andExpect(jsonPath(grc + ".KRI.metrics[?(@.name=='KRI_RED_CNT')].value", hasItem(2)))
           .andExpect(jsonPath(grc + ".KRI.metrics[?(@.name=='KRI_RED_PROP')].name", hasItem("KRI_RED_PROP")))
           .andExpect(jsonPath(grc + ".KRI.metrics[?(@.name=='KRI_GREEN_PROP')].name", hasItem("KRI_GREEN_PROP")));
    }

    // ── prevMonthNRRDetails ───────────────────────────────────────────────────────

    @Test
    void detail_prevMonthNRRDetails_fromPrevRowAndPrevFact() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.id", is(200)))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrrCalculated", is("Low")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrr", is("Med High")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrrOverlaid", is("Y")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.overlayJstfkn").value(nullValue()))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.ctrlEffRtn", is("Attention Needed To Satisfactory")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.assmtPeriod", is("June 2026")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.commentry", is("June commentary")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.grcMetrics.INC.metrics[?(@.name=='inc_is_sinp_count_l3m_mtd')].value", hasItem(5)))
           // Detail 200 (previous row) has no commentary-revision stamp seeded → both null.
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.commentaryRevisedBy").value(nullValue()))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.commentaryRevisedAt").value(nullValue()))
           // Assessment 10 has no PREV_ASSMT_NUM, so there is no baseline for its own change → N.A.
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.riskRatingChange", is("N.A")));
    }

    @Test
    void detail_prevMonthNRRDetails_null_whenNoMatchingPrevRow() throws Exception {
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails").value(nullValue()));
    }

    @Test
    void detail_prevMonthNRRDetails_null_whenNoPreviousAssessment() throws Exception {
        mvc.perform(get(URL_TPL, 12, 310).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails").value(nullValue()));
    }

    // ── liveNRRDetails ─────────────────────────────────────────────────────────────

    @Test
    void detail_liveNRRDetails_fromLatestFactAndModuleLatest() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.liveNRRDetails.nrr", is("Med Low")))
           .andExpect(jsonPath("$.data.liveNRRDetails.nrrOverlaid", is("N")))
           .andExpect(jsonPath("$.data.liveNRRDetails.overlayJstfkn").value(nullValue()))
           .andExpect(jsonPath("$.data.liveNRRDetails.lastRefreshed", is("2026-07-31")))
           .andExpect(jsonPath("$.data.liveNRRDetails.ctrlEffRtn", is("Satisfactory to Good")))
           .andExpect(jsonPath("$.data.liveNRRDetails.commentry", is("Live commentary")))
           // Live GRC metrics use each module's own latest row (INC @ 2026-07-31 → sinp 9).
           .andExpect(jsonPath("$.data.liveNRRDetails.grcMetrics.INC.metrics[?(@.name=='inc_is_sinp_count_l3m_mtd')].value", hasItem(9)))
           // Live INC nrr is the live row's rating as the stored DB value (Med Low).
           .andExpect(jsonPath("$.data.liveNRRDetails.grcMetrics.INC.nrr", is("Med Low")))
           // riskRatingChge is COMPUTED here: current INC rating (High) vs live INC rating (Med Low)
           // → less severe → "Improved".
           .andExpect(jsonPath("$.data.liveNRRDetails.grcMetrics.INC.riskRatingChge", is("Improved")));
    }

    @Test
    void detail_liveNRRDetails_null_whenNoFactForDimension() throws Exception {
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.liveNRRDetails").value(nullValue()));
    }

    @Test
    void detail_currentMonthGrcMetrics_allModulesDefaultedWhenNoModuleRows() throws Exception {
        // Row 301 (grp_l2, empty location) matches no module rows, so every module is still present
        // and fully populated: nrr "N.A", all metrics listed with null values. Shape never varies.
        String grc = "$.data.currentMonthNRRDetails.grcMetrics";
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrr").value(nullValue()))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrCalculated").value(nullValue()))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrOverlaid", is("N")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.overlayJstfkn").value(nullValue()))
           .andExpect(jsonPath(grc, aMapWithSize(4)))
           .andExpect(jsonPath(grc + ".RCSA.nrr", is("N.A")))
           .andExpect(jsonPath(grc + ".INC.nrr", is("N.A")))
           .andExpect(jsonPath(grc + ".INA.nrr", is("N.A")))
           .andExpect(jsonPath(grc + ".KRI.nrr", is("N.A")))
           // Every metric is still listed, each with a null value and an N.A change.
           .andExpect(jsonPath(grc + ".KRI.metrics", not(empty())))
           .andExpect(jsonPath(grc + ".KRI.metrics[?(@.name=='KRI_ACTIVE_CNT')].value", everyItem(nullValue())))
           .andExpect(jsonPath(grc + ".KRI.metrics[?(@.name=='KRI_ACTIVE_CNT')].riskRatingChge", hasItem("N.A")));
    }

    // ── bu / location category rules ────────────────────────────────────────────

    @Test
    void detail_bu_isGroup_forLocCategory() throws Exception {
        mvc.perform(get(URL_TPL, 11, 302).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.bu", is("All")))
           .andExpect(jsonPath("$.data.location", is("SG")));
    }

    @Test
    void detail_location_isGroup_forGrpCategory_andBuFromCategoryLevel() throws Exception {
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.bu", is("CBG")))
           .andExpect(jsonPath("$.data.location", is("All")));
    }
}
