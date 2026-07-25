package com.dbs.mot.grc.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code POST /landscape/assessments/generate}
 * (bulk assessment generation for all active landscapes).
 *
 * <h3>Seed data</h3>
 * <ul>
 *   <li>'Alpha' (id 1, level 2): 2 risk areas × ((2 BU × 2 loc) + 2 grp + 2 loc) = 16 rows</li>
 *   <li>'Beta'  (id 2, level 3): 1 risk area × ((1 BU × 1 loc) + 1 grp + 1 loc) = 3 rows</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkAssmtGenerationControllerTest {

    private static final String URL = "/landscape/assessments/generate";
    private static final DateTimeFormatter PERIOD_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    // A run in month M generates an assessment for the previous month (M-1) and links to M-2.
    private final String assessmentPeriod = YearMonth.now().minusMonths(1).format(PERIOD_FMT);
    private final String priorPeriod = YearMonth.now().minusMonths(2).format(PERIOD_FMT);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        jdbc.execute("DELETE FROM orl_biz_unit");

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '[{"groupName":"Conduct","isGroup":true,"riskAreas":[{"riskArea":"Market Abuse","riskClusters":["OR","LCS"]},{"riskArea":"AML, CFT and Sanctions","riskClusters":["OR"]}]}]',
                    'Technology,Operations',2,'SG,CN','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(2,'CFG002','Beta',DATE '2024-01-01',1,
                    '[{"groupName":"Conduct","isGroup":false,"riskAreas":[{"riskArea":"Market Abuse","riskClusters":["OR"]}]}]',
                    'DTI',3,'SG','seed')
                """);

        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,CREATED_BY) VALUES(10,'Technology',2,'Technology','seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,CREATED_BY) VALUES(11,'Operations',2,'Operations','seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,CREATED_BY) VALUES(12,'DTI',3,'Technology','DTI','seed')");
    }

    // ── Authentication ──────────────────────────────────────────────────────────

    @Test
    void missingUserId_returns401() throws Exception {
        mvc.perform(post(URL))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.message", containsString("X-EGRC-UserId")));
    }

    // ── Happy path ──────────────────────────────────────────────────────────────

    @Test
    void generateAll_generatesEveryActiveLandscape() throws Exception {
        mvc.perform(post(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.success", is(true)))
           .andExpect(jsonPath("$.message", containsString("Generated 2 of 2")))
           .andExpect(jsonPath("$.data.totalLandscapes", is(2)))
           .andExpect(jsonPath("$.data.generated", is(2)))
           .andExpect(jsonPath("$.data.skipped", is(0)))
           .andExpect(jsonPath("$.data.results", hasSize(2)))
           .andExpect(jsonPath("$.data.results[?(@.lndscpNm=='Alpha')].status", contains("GENERATED")))
           .andExpect(jsonPath("$.data.results[?(@.lndscpNm=='Alpha')].detailRowCount", contains(16)))
           .andExpect(jsonPath("$.data.results[?(@.lndscpNm=='Beta')].detailRowCount", contains(3)));

        Integer assmtCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt WHERE ASSEMT_PERIOD=? AND CREATED_BY='tester'",
                Integer.class, assessmentPeriod);
        assert assmtCount != null && assmtCount == 2;

        Integer detailCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details", Integer.class);
        assert detailCount != null && detailCount == 19; // 16 (Alpha) + 3 (Beta)
    }

    @Test
    void generateAll_noActiveLandscapes_returns200WithEmptyResults() throws Exception {
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        mvc.perform(post(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message", containsString("nothing to generate")))
           .andExpect(jsonPath("$.data.totalLandscapes", is(0)))
           .andExpect(jsonPath("$.data.results", hasSize(0)));
    }

    // ── Skip conditions ─────────────────────────────────────────────────────────

    @Test
    void generateAll_secondRunSameMonth_skipsAllAsAlreadyExists() throws Exception {
        mvc.perform(post(URL).header("X-EGRC-UserId", "tester")).andExpect(status().isOk());

        mvc.perform(post(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.generated", is(0)))
           .andExpect(jsonPath("$.data.skipped", is(2)))
           .andExpect(jsonPath("$.data.results[*].status",
                   everyItem(is("SKIPPED_ALREADY_EXISTS"))));

        // Still only one assessment per landscape.
        Integer assmtCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt", Integer.class);
        assert assmtCount != null && assmtCount == 2;
    }

    @Test
    void generateAll_ambiguousConfig_skipsThatLandscapeAndContinues() throws Exception {
        // Second ACTIVE, currently-effective config for 'Alpha' → ambiguous; 'Beta' unaffected.
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(6,'CFG001','Alpha',DATE '2025-01-01',2,
                    '[{"groupName":"Conduct","isGroup":false,"riskAreas":[{"riskArea":"Market Abuse","riskClusters":["OR"]}]}]','SG','seed')
                """);

        mvc.perform(post(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.totalLandscapes", is(2)))
           .andExpect(jsonPath("$.data.generated", is(1)))
           .andExpect(jsonPath("$.data.skipped", is(1)))
           .andExpect(jsonPath("$.data.results[?(@.lndscpNm=='Alpha')].status",
                   contains("SKIPPED_AMBIGUOUS_CONFIG")))
           .andExpect(jsonPath("$.data.results[?(@.lndscpNm=='Alpha')].message",
                   contains(containsString("Multiple active configs"))))
           .andExpect(jsonPath("$.data.results[?(@.lndscpNm=='Beta')].status",
                   contains("GENERATED")));

        // Alpha skipped, Beta committed — one landscape's skip does not roll back the other.
        Integer alphaCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt WHERE LNDSCP_NUM=1", Integer.class);
        Integer betaCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt WHERE LNDSCP_NUM=2", Integer.class);
        assert alphaCount != null && alphaCount == 0;
        assert betaCount != null && betaCount == 1;
    }

    @Test
    void generateAll_inactiveAndExpiredConfigs_areExcluded() throws Exception {
        // DEACTIVATED and out-of-window configs must not appear in the run at all.
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,STATUS,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(3,'CFG003','Gamma',DATE '2024-01-01',1,'DEACTIVATED',
                    '[{"groupName":"Conduct","isGroup":false,"riskAreas":[{"riskArea":"Market Abuse","riskClusters":["OR"]}]}]','SG','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,EFFECT_END_DT,VERSION,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(4,'CFG004','Delta',DATE '2020-01-01',DATE '2020-12-31',1,
                    '[{"groupName":"Conduct","isGroup":false,"riskAreas":[{"riskArea":"Market Abuse","riskClusters":["OR"]}]}]','SG','seed')
                """);

        mvc.perform(post(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.totalLandscapes", is(2)))
           .andExpect(jsonPath("$.data.results[*].lndscpNm",
                   containsInAnyOrder("Alpha", "Beta")));
    }

    // ── Generation behaviour (via generateForDim, exercised through the bulk run) ──

    @Test
    void generation_producesL2GrpAndLocCategories_withEmptyDimsAsBlank() throws Exception {
        mvc.perform(post(URL).header("X-EGRC-UserId", "tester")).andExpect(status().isOk());

        // L2 rows: BU set, location set (Alpha = LNDSCP_NUM 1, one per risk area).
        assertRowCount(1, "category='L2' AND ORL_BU_NM_L2='Technology' AND LOCATION='SG'", 2);
        // grp_l2 rows: BU set, empty location stored as '' (never null).
        assertRowCount(1, "category='grp_l2' AND ORL_BU_NM_L2='Operations' AND LOCATION=''", 2);
        // loc rows: empty BU stored as '', location set.
        assertRowCount(1, "category='loc' AND ORL_BU_NM_L2='' AND LOCATION='CN'", 2);
        // Empty dimension columns are never null.
        assertRowCount(1, "ORL_BU_NM_L2 IS NULL OR ORL_BU_NM_L3 IS NULL OR ORL_BU_NM_L4 IS NULL OR LOCATION IS NULL", 0);
    }

    @Test
    void generation_writesThinRows_openStatusNoOverlay() throws Exception {
        mvc.perform(post(URL).header("X-EGRC-UserId", "tester")).andExpect(status().isOk());
        assertRowCount(1, "d.STATUS='Open' AND d.OVRLY_NET_RISK_RTNG IS NULL", 16);
    }

    @Test
    void generation_resolvesBuHierarchy_level3() throws Exception {
        mvc.perform(post(URL).header("X-EGRC-UserId", "tester")).andExpect(status().isOk());
        // Beta (LNDSCP_NUM 2, level 3): DTI resolves to L2=Technology, L3=DTI; empty L4 = ''.
        assertRowCount(2, "category='L3' AND ORL_BU_NM_L2='Technology' AND ORL_BU_NM_L3='DTI' AND ORL_BU_NM_L4=''", 1);
        assertRowCount(2, "category='grp_l3'", 1);
        assertRowCount(2, "category='loc'", 1);
    }

    @Test
    void generation_unresolvedBu_keepsNameAtOwnLevel() throws Exception {
        jdbc.execute("DELETE FROM orl_biz_unit WHERE BU_NM='Technology'");
        mvc.perform(post(URL).header("X-EGRC-UserId", "tester")).andExpect(status().isOk());
        // Technology unresolved → still present with L2='Technology', empty L3/L4 = ''.
        assertRowCount(1, "ORL_BU_NM_L2='Technology' AND ORL_BU_NM_L3='' AND ORL_BU_NM_L4='' AND category='L2'", 4);
    }

    @Test
    void generation_duplicateEmptyDimensionRow_rejectedByUniqueIndex() throws Exception {
        mvc.perform(post(URL).header("X-EGRC-UserId", "tester")).andExpect(status().isOk());
        Long assmtId = jdbc.queryForObject("SELECT id FROM orl_lndscp_assmt WHERE LNDSCP_NUM=2", Long.class);

        // Re-inserting a 'loc' row (empty BU path stored as '') must violate the unique index —
        // proving '' makes the index effective where NULL would not.
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.execute(
                "INSERT INTO orl_lndscp_assmt_details "
                + "(lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,LOCATION,category,STATUS,CREATED_BY) "
                + "VALUES(" + assmtId + ",'Market Abuse','','','','SG','loc','Open','seed')"));
    }

    // ── PREV_ASSMT_NUM linkage ────────────────────────────────────────────────────

    @Test
    void generation_linksPreviousMonthAssessment_viaPrevAssmtNum() throws Exception {
        // Previous month's assessment for Alpha (LNDSCP_NUM 1).
        jdbc.update("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(500,1,?,'Open','seed')",
                priorPeriod);

        mvc.perform(post(URL).header("X-EGRC-UserId", "tester")).andExpect(status().isOk());

        Long prev = jdbc.queryForObject(
                "SELECT PREV_ASSMT_NUM FROM orl_lndscp_assmt WHERE LNDSCP_NUM=1 AND ASSEMT_PERIOD=?",
                Long.class, assessmentPeriod);
        assert prev != null && prev == 500L;
    }

    @Test
    void generation_noPreviousAssessment_prevAssmtNumNull() throws Exception {
        mvc.perform(post(URL).header("X-EGRC-UserId", "tester")).andExpect(status().isOk());
        Long prev = jdbc.queryForObject(
                "SELECT PREV_ASSMT_NUM FROM orl_lndscp_assmt WHERE LNDSCP_NUM=1 AND ASSEMT_PERIOD=?",
                Long.class, assessmentPeriod);
        assert prev == null;
    }

    @Test
    void generation_previousAssessmentOfOtherLandscape_isNotLinked() throws Exception {
        // A previous-month assessment exists, but for LNDSCP_NUM=2 — must not link to Alpha (id 1).
        jdbc.update("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(501,2,?,'Open','seed')",
                priorPeriod);

        mvc.perform(post(URL).header("X-EGRC-UserId", "tester")).andExpect(status().isOk());

        Long prev = jdbc.queryForObject(
                "SELECT PREV_ASSMT_NUM FROM orl_lndscp_assmt WHERE LNDSCP_NUM=1 AND ASSEMT_PERIOD=?",
                Long.class, assessmentPeriod);
        assert prev == null;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void assertRowCount(long lndscpNum, String whereClause, int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details d JOIN orl_lndscp_assmt a ON d.lndscp_assmt_id=a.id "
                + "WHERE a.LNDSCP_NUM=" + lndscpNum + " AND (" + whereClause + ")", Integer.class);
        assert count != null && count == expected
                : "Expected " + expected + " rows for [" + whereClause + "] but got " + count;
    }
}
