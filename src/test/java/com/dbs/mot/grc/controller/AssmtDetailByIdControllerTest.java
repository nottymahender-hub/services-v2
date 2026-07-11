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
 * <h3>Seed data</h3>
 * <ul>
 *   <li>Assmt 10 ("June 2026", dim 1) — the previous assessment, with detail row 200
 *       matching row 300's dimension key (OVRLY='Med High', CAL='Low', CTRL='Improving').</li>
 *   <li>Assmt 11 ("July 2026", dim 1, PREV_ASSMT_NUM=10) — the current assessment:
 *     <ul>
 *       <li>row 300 (L2): full data incl. LV_* columns, OVRLY set, GRC_METRICS JSON;</li>
 *       <li>row 301 (grp_l2): no OVRLY, no LV_*, no prev match;</li>
 *       <li>row 302 (loc): bu must resolve to the literal "Group".</li>
 *     </ul></li>
 *   <li>Assmt 12 ("July 2026", dim 2, no PREV_ASSMT_NUM) with row 310 — no-previous case.</li>
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

        // Previous (June) and current (July) assessments, linked via PREV_ASSMT_NUM.
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(10,1,'June 2026','Closed','seed')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,PREV_ASSMT_NUM,CREATED_BY) VALUES(11,1,'July 2026','Open',10,'seed')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(12,2,'July 2026','Open','seed')");

        // Previous assessment's row matching row 300's dimension key.
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,
                     CAL_NET_RISK_RTNG,OVRLY_NET_RISK_RTNG,CTRL_EFF_RTN,STATUS,CREATED_BY)
                VALUES(200,10,'AML Sanctions','CBG','SG','L2','Low','Med High','Improving','Completed','seed')
                """);

        // Current row 300 — full drill-down data.
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,
                     CAL_NET_RISK_RTNG,OVRLY_NET_RISK_RTNG,OVRLY_JSTFKN,CTRL_EFF_RTN,
                     LV_NET_RISK_RTNG,LV_LST_RFRSH_DT_TM,LV_CTRL_EFF_RTN,
                     COMMENTARY,REVISED_COMMENTARY,GRC_METRICS,
                     STATUS,CREATED_BY,UPDATE_DT_TM,UPDATED_BY)
                VALUES(300,11,'AML Sanctions','CBG','SG','L2',
                       'Low','High','Overlaid due to audit findings','Deteriorated',
                       'Med Low',TIMESTAMP '2026-07-04 08:30:00','Stable',
                       'July commentary','Revised July commentary',
                       '{"INC":{"nrr":"High","inc_is_sinp_count_l3m_mtd":{"count":7,"riskRatingChange":"Improved"}}}',
                       'Open','seed',TIMESTAMP '2026-07-05 09:00:00','user1')
                """);
        // Current row 301 — grp_l2, minimal data, no prev match.
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,
                     CAL_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(301,11,'AML Sanctions','CBG',NULL,'grp_l2','Low','Open','seed')
                """);
        // Current row 302 — category='loc': bu must be the literal "Group".
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,LOCATION,category,CAL_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(302,11,'AML Sanctions','SG','loc','Low','Open','seed')
                """);
        // Assessment 12 (no PREV_ASSMT_NUM) row.
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,
                     CAL_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(310,12,'AML Sanctions','CBG','SG','L2','Low','Open','seed')
                """);
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
           .andExpect(jsonPath("$.data.summary", is("July commentary")))
           .andExpect(jsonPath("$.data.revisedSummary", is("Revised July commentary")))
           .andExpect(jsonPath("$.data.category", is("L2")))
           .andExpect(jsonPath("$.data.nrrOverlaid", is("Y")))
           .andExpect(jsonPath("$.data.overlayJustfkn", is("Overlaid due to audit findings")));
    }

    @Test
    void detail_grcMetrics_isParsedJsonObject_notString() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.grcMetrics").isMap())
           .andExpect(jsonPath("$.data.grcMetrics.INC.nrr", is("High")))
           .andExpect(jsonPath("$.data.grcMetrics.INC.inc_is_sinp_count_l3m_mtd.count", is(7)))
           .andExpect(jsonPath("$.data.grcMetrics.INC.inc_is_sinp_count_l3m_mtd.riskRatingChange", is("Improved")));
    }

    // ── NRR blocks ──────────────────────────────────────────────────────────────

    @Test
    void detail_currentMonthNRRDetails_mappedFromRowAndAssmtPeriod() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrrCalculated", is("Low")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.nrr", is("High")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.ctrlEffRtn", is("Deteriorated")))
           .andExpect(jsonPath("$.data.currentMonthNRRDetails.assmtPeriod", is("July 2026")));
    }

    @Test
    void detail_prevMonthNRRDetails_fromMatchedPrevRow() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrrCalculated", is("Low")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrr", is("Med High")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.ctrlEffRtn", is("Improving")))
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.assmtPeriod", is("June 2026")));
    }

    @Test
    void detail_prevMonthNRRDetails_null_whenNoMatchingPrevRow() throws Exception {
        // Row 301 (grp_l2) has no dimension match in assessment 10.
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrrCalculated").doesNotExist());
    }

    @Test
    void detail_prevMonthNRRDetails_null_whenNoPreviousAssessment() throws Exception {
        // Assessment 12 has PREV_ASSMT_NUM=null.
        mvc.perform(get(URL_TPL, 12, 310).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.prevMonthNRRDetails.nrrCalculated").doesNotExist());
    }

    @Test
    void detail_liveNRRDetails_mappedFromLvColumns() throws Exception {
        mvc.perform(get(URL_TPL, 11, 300).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.liveNRRDetails.nrr", is("Med Low")))
           .andExpect(jsonPath("$.data.liveNRRDetails.lastRefreshed", startsWith("2026-07-04T08:30:00")))
           .andExpect(jsonPath("$.data.liveNRRDetails.ctrlEffRtn", is("Stable")));
    }

    @Test
    void detail_liveNRRDetails_fieldsNull_whenLvColumnsEmpty() throws Exception {
        mvc.perform(get(URL_TPL, 11, 301).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.liveNRRDetails.nrr", nullValue()));
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
