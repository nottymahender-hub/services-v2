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
 * Integration tests for {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}}
 * (drill-down view of one assessment detail row).
 *
 * <p>Computed values now come from {@code fact_orl}, matched by dimension + business date
 * (an assessment's {@code CREATE_DT_TM} date for its month; the latest {@code biz_dt} for
 * the live block).
 *
 * <h3>Seed data (dimension: AML Sanctions / CBG / SG)</h3>
 * <ul>
 *   <li>Assmt 10 ("June 2026", created 2026-06-30) — previous assessment; detail 200
 *       (OVRLY='Med High'); fact @2026-06-30 (CAL='Low', CTRL='Improving').</li>
 *   <li>Assmt 11 ("July 2026", created 2026-07-15, PREV=10) — current; detail 300
 *       (OVRLY='High'); fact @2026-07-15 (CAL='Low', CTRL='Deteriorated', GRC json);
 *       a later fact @2026-07-31 (CAL='Med Low', CTRL='Stable') is the live snapshot.</li>
 *   <li>Detail 301 (grp_l2, no matching fact) and 302 (loc) under assmt 11.</li>
 *   <li>Assmt 12 ("July 2026", dim 2, no PREV) with detail 310.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssmtDetailByIdControllerTest {

    private static final String URL_TPL = "/landscape/{assmtId}/{detailId}";
    private static final String GRC_JSON =
            "{\"INC\":{\"nrr\":\"High\",\"inc_is_sinp_count_l3m_mtd\":{\"count\":7,\"riskRatingChange\":\"Improved\"}}}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        jdbc.execute("DELETE FROM fact_orl");

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '{"AML Sanctions":["OR"]}','CBG',2,'SG','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(2,'CFG002','Beta',DATE '2024-01-01',1,
                    '{"AML Sanctions":["OR"]}','CBG',2,'SG','seed')
                """);

        // Assessments with explicit CREATE_DT_TM (their fact_orl business date).
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY,CREATE_DT_TM) VALUES(10,1,'June 2026','Closed','seed',TIMESTAMP '2026-06-30 00:00:00')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,PREV_ASSMT_NUM,CREATED_BY,CREATE_DT_TM) VALUES(11,1,'July 2026','Open',10,'seed',TIMESTAMP '2026-07-15 00:00:00')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY,CREATE_DT_TM) VALUES(12,2,'July 2026','Open','seed',TIMESTAMP '2026-07-15 00:00:00')");

        // fact_orl for dimension (AML Sanctions, CBG, -, -, SG):
        insertFact("2026-06-30", "Low", "Improving", "June commentary", null);       // prev month
        insertFact("2026-07-15", "Low", "Deteriorated", "July commentary", GRC_JSON); // current month
        insertFact("2026-07-31", "Med Low", "Stable", "Live commentary", GRC_JSON);   // latest → live

        // Previous assessment (10) thin row matching the dimension; OVRLY='Med High'.
        insertDetail(200, 10, "CBG", "SG", "L2", "Med High", null, "Completed", null, null);
        // Current row 300 — overlay + revised commentary + audit fields.
        insertDetail(300, 11, "CBG", "SG", "L2", "High", "Overlaid due to audit findings",
                "Open", "Revised July commentary", "user1");
        // Row 301 — grp_l2 (L2=CBG, no location) → no matching fact.
        insertDetail(301, 11, "CBG", null, "grp_l2", null, null, "Open", null, null);
        // Row 302 — loc: bu must resolve to the literal "Group".
        insertDetailNoBu(302, 11, "SG", "loc", "Open");
        // Assessment 12 (no PREV) row.
        insertDetail(310, 12, "CBG", "SG", "L2", null, null, "Open", null, null);
    }

    /** Thin detail row with a BU (L2). */
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

    /** Thin detail row without a BU (loc category); empty BU columns default to ''. */
    private void insertDetailNoBu(long id, long assmtId, String loc, String category, String status) {
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,LOCATION,category,STATUS,CREATED_BY)
                VALUES(%d,%d,'AML Sanctions',%s,%s,%s,'seed')
                """.formatted(id, assmtId, qDim(loc), q(category), q(status)));
    }

    /** fact_orl row for the (AML Sanctions, CBG, -, -, SG) dimension at the given biz_dt. */
    private void insertFact(String bizDt, String cal, String ctrl, String commentary, String grcJson) {
        jdbc.execute("""
                INSERT INTO fact_orl
                    (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,CAL_NET_RISK_RTNG,CTRL_EFF_RTN,COMMENTARY,GRC_METRICS)
                VALUES(DATE %s,'AML Sanctions','CBG','SG','L2',%s,%s,%s,%s)
                """.formatted(q(bizDt), q(cal), q(ctrl), q(commentary), q(grcJson)));
    }

    private String q(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }

    /** SQL literal for a NOT NULL dimension column: null becomes the empty string ''. */
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
        // Row 310 exists, but under assessment 12 — asking for it under 11 is a 404.
        mvc.perform(get(URL_TPL, 11, 310).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", containsString("310")));
    }

    @Test
    void nonNumericDetailId_returns400() throws Exception {
        mvc.perform(get("/landscape/11/abc").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isBadRequest());
    }

    // ── Happy path — flat fields ────────────────────────────────────────────────

    @Test
    void detail_flatFields_mappedFromRow() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.id", is(300)))
           .andExpect(jsonPath("$.data.riskArea", is("AML Sanctions")))
           .andExpect(jsonPath("$.data.bu", is("CBG")))
           .andExpect(jsonPath("$.data.location", is("SG")))
           .andExpect(jsonPath("$.data.status", is("Open")))
           .andExpect(jsonPath("$.data.lastModified", startsWith("2026-07-05T09:00:00")))
           .andExpect(jsonPath("$.data.lastModifiedBy", is("user1")))
           .andExpect(jsonPath("$.data.category", is("L2")))
           .andExpect(jsonPath("$.data.nrrOverlaid", is("Y")))
           .andExpect(jsonPath("$.data.overlayJustfkn", is("Overlaid due to audit findings")));
    }

    // ── currentMonthNRRDetails (fact_orl at the assessment's biz date) ───────────

    @Test
    void detail_currentMonthNRRDetails_fromFactAndOverlay() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrCalculated", is("Low")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrr", is("High")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.ctrlEffRtn", is("Deteriorated")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.assmtPeriod", is("July 2026")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.commentry", is("July commentary")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.revisedCommentry", is("Revised July commentary")));
    }

    @Test
    void detail_currentMonthGrcMetrics_isParsedJsonObject_notString() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics").isMap())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.INC.nrr", is("High")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.INC.inc_is_sinp_count_l3m_mtd.count", is(7)))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics.INC.inc_is_sinp_count_l3m_mtd.riskRatingChange", is("Improved")));
    }

    // ── prevMonthNRRDetails (prev detail + prev-month fact) ──────────────────────

    @Test
    void detail_prevMonthNRRDetails_fromPrevRowAndPrevFact() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.id", is(200)))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrrCalculated", is("Low")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrr", is("Med High")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.ctrlEffRtn", is("Improving")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.assmtPeriod", is("June 2026")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.commentry", is("June commentary")));
    }

    @Test
    void detail_prevMonthNRRDetails_null_whenNoMatchingPrevRow() throws Exception {
        // Row 301 (grp_l2, no location) has no dimension match in assessment 10.
        // The block is rendered as JSON null (present, not omitted).
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails").value(nullValue()));
    }

    @Test
    void detail_prevMonthNRRDetails_null_whenNoPreviousAssessment() throws Exception {
        // Assessment 12 has PREV_ASSMT_NUM=null.
        mvc.perform(get(URL_TPL, 12, 310).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails").value(nullValue()));
    }

    // ── liveNRRDetails (latest biz_dt fact) ──────────────────────────────────────

    @Test
    void detail_liveNRRDetails_fromLatestFact() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.liveNRRDetails.nrr", is("Med Low")))
           .andExpect(jsonPath("$.data.liveNRRDetails.lastRefreshed", is("2026-07-31")))
           .andExpect(jsonPath("$.data.liveNRRDetails.ctrlEffRtn", is("Stable")))
           .andExpect(jsonPath("$.data.liveNRRDetails.commentry", is("Live commentary")));
    }

    @Test
    void detail_liveNRRDetails_null_whenNoFactForDimension() throws Exception {
        // Row 301 (grp_l2, no location) has no fact_orl row at all → block rendered as null.
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.liveNRRDetails").value(nullValue()));
    }

    @Test
    void detail_nullValuedPropertiesAreIncluded_notOmitted() throws Exception {
        // Row 301 has no overlay and no matching facts: within the present current-month
        // block, the null properties must still appear (NON_NULL filtering is off).
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrr").value(nullValue()))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrCalculated").value(nullValue()))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.grcMetrics").value(nullValue()))
           .andExpect(jsonPath("$.data.overlayJustfkn").value(nullValue()))
           .andExpect(jsonPath("$.data.lastModifiedBy").value(nullValue()));
    }

    // ── bu / location category rules ────────────────────────────────────────────

    @Test
    void detail_bu_isGroup_forLocCategory() throws Exception {
        mvc.perform(get(URL_TPL, 11, 302).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.bu", is("Group")))
           .andExpect(jsonPath("$.data.location", is("SG")));
    }

    @Test
    void detail_location_isGroup_forGrpCategory_andBuFromCategoryLevel() throws Exception {
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.bu", is("CBG")))
           .andExpect(jsonPath("$.data.location", is("Group")));
    }

    @Test
    void detail_nrrOverlaid_isN_whenNotOverlaid() throws Exception {
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.nrrOverlaid", is("N")));
    }
}
