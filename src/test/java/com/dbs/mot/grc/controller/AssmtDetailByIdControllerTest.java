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

    private static final String URL_TPL = "/landscape/{assmtId}/{detailId}";

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

        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY,CREATE_DT_TM) VALUES(10,1,'June 2026','Closed','seed',TIMESTAMP '2026-06-30 00:00:00')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,PREV_ASSMT_NUM,CREATED_BY,CREATE_DT_TM) VALUES(11,1,'July 2026','Open',10,'seed',TIMESTAMP '2026-07-15 00:00:00')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY,CREATE_DT_TM) VALUES(12,2,'July 2026','Open','seed',TIMESTAMP '2026-07-15 00:00:00')");

        // fact_orl (row-level values) for (AML Sanctions, CBG, -, -, SG).
        insertFact("2026-06-30", "Low", "Attention Needed To Satisfactory", "June commentary"); // prev
        insertFact("2026-07-15", "Low", "Good", "July commentary");                             // current
        insertFact("2026-07-31", "Med Low", "Satisfactory to Good", "Live commentary");         // live

        // Per-module rows for the same dimension.
        insertIncFact("2026-06-30", "Low", "Improved", 5);
        insertIncFact("2026-07-15", "High", "Improved", 7);
        insertIncFact("2026-07-31", "Med Low", "Stable", 9);
        insertKriFact("2026-07-15", "High", "Improved", 4, 2, 1);
        insertRcsaFact("2026-07-15", "High", "Stable", 3);

        insertDetail(200, 10, "CBG", "SG", "L2", "Med High", null, "Completed", null, null);
        insertDetail(300, 11, "CBG", "SG", "L2", "High", "Overlaid due to audit findings",
                "Open", "Revised July commentary", "user1");
        insertDetail(301, 11, "CBG", null, "grp_l2", null, null, "Open", null, null);
        insertDetailNoBu(302, 11, "SG", "loc", "Open");
        insertDetail(310, 12, "CBG", "SG", "L2", null, null, "Open", null, null);
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

    private void insertIncFact(String bizDt, String nrr, String chge, int sinp) {
        jdbc.execute("""
                INSERT INTO inc_fact_orl
                    (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RTNG,RISK_RTNG_CHGE,inc_is_sinp_count_l3m_mtd)
                VALUES(DATE %s,'AML Sanctions','CBG','SG',%s,%s,%d)
                """.formatted(q(bizDt), q(nrr), q(chge), sinp));
    }

    private void insertKriFact(String bizDt, String nrr, String chge, int active, int red, int green) {
        jdbc.execute("""
                INSERT INTO kri_fact_orl
                    (biz_dt,ORL_RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RATING,RISK_RTNG_CHGE,
                     KRI_ACTIVE_CNT,KRI_RED_CNT,KRI_GREEN_CNT)
                VALUES(DATE %s,'AML Sanctions','CBG','SG',%s,%s,%d,%d,%d)
                """.formatted(q(bizDt), q(nrr), q(chge), active, red, green));
    }

    private void insertRcsaFact(String bizDt, String nrr, String chge, int highRisk) {
        jdbc.execute("""
                INSERT INTO rcsa_fact_orl
                    (biz_date,orl_risk_area,orl_unit_l2,orl_location,NRR,RISK_RTNG_CHGE,combined_count_high_risk)
                VALUES(DATE %s,'AML Sanctions','CBG','SG',%s,%s,%d)
                """.formatted(q(bizDt), q(nrr), q(chge), highRisk));
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
    void detailNotBelongingToAssessment_returns404() throws Exception {
        mvc.perform(get(URL_TPL, 11, 310).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", containsString("310")));
    }

    @Test
    void nonNumericDetailId_returns400() throws Exception {
        mvc.perform(get("/landscape/11/abc").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isBadRequest());
    }

    // ── Flat fields + landscapeId ─────────────────────────────────────────────────

    @Test
    void detail_flatFields_mappedFromRow() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.id", is(300)))
           .andExpect(jsonPath("$.data.landscapeId", is(1)))
           .andExpect(jsonPath("$.data.riskArea", is("AML Sanctions")))
           .andExpect(jsonPath("$.data.bu", is("CBG")))
           .andExpect(jsonPath("$.data.location", is("SG")))
           .andExpect(jsonPath("$.data.status", is("Open")))
           .andExpect(jsonPath("$.data.lastModified", startsWith("2026-07-05T09:00:00")))
           .andExpect(jsonPath("$.data.lastModifiedBy", is("user1")))
           .andExpect(jsonPath("$.data.category", is("L2")))
           // Overlay fields are no longer top-level (moved into the month blocks).
           .andExpect(jsonPath("$.data.nrrOverlaid").doesNotExist())
           .andExpect(jsonPath("$.data.overlayJustfkn").doesNotExist());
    }

    // ── currentMonthNRRDetails ────────────────────────────────────────────────────

    @Test
    void detail_currentMonthNRRDetails_fromFactAndOverlay() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrCalculated", is("Low Risk")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrr", is("High Risk")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrOverlaid", is("Y")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.overlayJstfkn", is("Overlaid due to audit findings")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.ctrlEffRtn", is("Good")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.assmtPeriod", is("July 2026")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.commentry", is("July commentary")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.revisedCommentry", is("Revised July commentary")));
    }

    @Test
    void detail_currentMonthGrcMetrics_assembledFromModuleTables() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics").isMap())
           // INC block: nrr + risk_rating_chge + metric
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.INC.nrr", is("High")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.INC.risk_rating_chge", is("Improved")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.INC.inc_is_sinp_count_l3m_mtd", is(7)))
           // RCSA block
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.RCSA.combined_count_high_risk", is(3)))
           // KRI block with derived proportions present
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.KRI.KRI_RED_CNT", is(2)))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.KRI.KRI_RED_PROP").exists())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.KRI.KRI_GREEN_PROP").exists());
    }

    // ── prevMonthNRRDetails ───────────────────────────────────────────────────────

    @Test
    void detail_prevMonthNRRDetails_fromPrevRowAndPrevFact() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.id", is(200)))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrrCalculated", is("Low Risk")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrr", is("Medium-High Risk")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrrOverlaid", is("Y")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.overlayJstfkn").value(nullValue()))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.ctrlEffRtn", is("Attention Needed To Satisfactory")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.assmtPeriod", is("June 2026")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.commentry", is("June commentary")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.grcMetrics.INC.inc_is_sinp_count_l3m_mtd", is(5)));
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
           .andExpect(jsonPath("$.data.liveNRRDetails.nrr", is("Medium-Low Risk")))
           .andExpect(jsonPath("$.data.liveNRRDetails.nrrOverlaid", is("N")))
           .andExpect(jsonPath("$.data.liveNRRDetails.overlayJstfkn").value(nullValue()))
           .andExpect(jsonPath("$.data.liveNRRDetails.lastRefreshed", is("2026-07-31")))
           .andExpect(jsonPath("$.data.liveNRRDetails.ctrlEffRtn", is("Satisfactory to Good")))
           .andExpect(jsonPath("$.data.liveNRRDetails.commentry", is("Live commentary")))
           // Live GRC metrics use each module's own latest row (INC @ 2026-07-31 → sinp 9).
           .andExpect(jsonPath("$.data.liveNRRDetails.grcMetrics.INC.inc_is_sinp_count_l3m_mtd", is(9)));
    }

    @Test
    void detail_liveNRRDetails_null_whenNoFactForDimension() throws Exception {
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.liveNRRDetails").value(nullValue()));
    }

    @Test
    void detail_currentMonthGrcMetrics_emptyWhenNoModuleRows() throws Exception {
        // Row 301 (grp_l2, empty location) matches no module rows → empty grcMetrics map.
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrr").value(nullValue()))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrCalculated").value(nullValue()))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrOverlaid", is("N")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.overlayJstfkn").value(nullValue()))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics", anEmptyMap()));
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
