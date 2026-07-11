package com.dbs.mot.grc.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code POST /landscape/{lndscpNm}/assessments/generate}
 * (single-landscape assessment generation by landscape name).
 *
 * <h3>Seed data (landscape 'Alpha', level 2)</h3>
 * <ul>
 *   <li>RISK_AREA JSON: {@code {"Market Abuse":["OR","LCS"],"AML, CFT and Sanctions":["OR"]}}</li>
 *   <li>BIZ_UNITS: {@code Technology,Operations}; LOCATIONS: {@code SG,CN}; BIZ_UNIT_LVL: 2</li>
 * </ul>
 * Expansion per risk area = (2 BU × 2 loc) + 2 grp + 2 loc = 8 → 16 detail rows total.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LandscapeAssmtGenerationControllerTest {

    private static final String URL_TPL = "/landscape/{name}/assessments/generate";
    private static final DateTimeFormatter PERIOD_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final String currentPeriod = YearMonth.now().format(PERIOD_FMT);
    private final String previousPeriod = YearMonth.now().minusMonths(1).format(PERIOD_FMT);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        jdbc.execute("DELETE FROM orl_biz_unit");
        jdbc.execute("DELETE FROM inc_fact_orl");
        jdbc.execute("DELETE FROM ina_fact_orl");
        jdbc.execute("DELETE FROM kri_fact_orl");
        jdbc.execute("DELETE FROM rcsa_fact_orl");

        // Landscape 'Alpha' (id 1) — level 2, ACTIVE, effective (defaults: STATUS=ACTIVE, END=9999-12-31)
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '{"Market Abuse":["OR","LCS"],"AML, CFT and Sanctions":["OR"]}',
                    'Technology,Operations',2,'SG,CN','seed')
                """);
        // Landscape 'Beta' (id 2) — level 3 (for category-variant test)
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(2,'CFG002','Beta',DATE '2024-01-01',1,
                    '{"Market Abuse":["OR"]}','DTI',3,'SG','seed')
                """);

        // Business units — level 2 (Technology, Operations) and level 3 (DTI under Technology)
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,CREATED_BY) VALUES(10,'Technology',2,'Technology','seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,CREATED_BY) VALUES(11,'Operations',2,'Operations','seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,CREATED_BY) VALUES(12,'DTI',3,'Technology','DTI','seed')");
    }

    // ── Authentication / config resolution ─────────────────────────────────────

    @Test
    void missingUserId_returns401() throws Exception {
        mvc.perform(post(URL_TPL, "Alpha"))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.message", containsString("X-EGRC-UserId")));
    }

    @Test
    void unknownLandscapeName_returns404() throws Exception {
        mvc.perform(post(URL_TPL, "NoSuchLandscape").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", containsString("NoSuchLandscape")));
    }

    @Test
    void deactivatedConfig_returns404() throws Exception {
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,STATUS,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(3,'CFG003','Gamma',DATE '2024-01-01',1,'DEACTIVATED',
                    '{"Market Abuse":["OR"]}','SG','seed')
                """);
        mvc.perform(post(URL_TPL, "Gamma").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", containsString("Gamma")));
    }

    @Test
    void configNotEffectiveToday_returns404() throws Exception {
        // Expired window (in the past) and a future window — neither contains today.
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,EFFECT_END_DT,VERSION,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(4,'CFG004','Delta',DATE '2020-01-01',DATE '2020-12-31',1,
                    '{"Market Abuse":["OR"]}','SG','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,EFFECT_END_DT,VERSION,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(5,'CFG005','Delta',DATE '2099-01-01',DATE '2099-12-31',2,
                    '{"Market Abuse":["OR"]}','SG','seed')
                """);
        mvc.perform(post(URL_TPL, "Delta").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.message", containsString("Delta")));
    }

    @Test
    void multipleEffectiveConfigs_returns409WithoutGenerating() throws Exception {
        // Second ACTIVE, currently-effective config for 'Alpha' → ambiguous.
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(6,'CFG001','Alpha',DATE '2025-01-01',2,
                    '{"Market Abuse":["OR"]}','SG','seed')
                """);
        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.message", containsString("Multiple active configs")));

        Integer assmtCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt", Integer.class);
        assert assmtCount != null && assmtCount == 0;
    }

    @Test
    void oldIdBasedGenerateEndpoint_isRemoved_returns405() throws Exception {
        // The path template still exists for GET (assessment details), so POST is 405.
        mvc.perform(post("/landscape/1/assessments").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isMethodNotAllowed());
    }

    // ── Happy path (level 2) ────────────────────────────────────────────────────

    @Test
    void generate_level2_creates16DetailRows() throws Exception {
        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.success", is(true)))
           .andExpect(jsonPath("$.data.lndscpNm", is("Alpha")))
           .andExpect(jsonPath("$.data.lndscpNum", is(1)))
           .andExpect(jsonPath("$.data.detailRowCount", is(16)))
           .andExpect(jsonPath("$.data.assmtPeriod", is(currentPeriod)));

        Integer assmtCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt WHERE LNDSCP_NUM=1 AND ASSEMT_PERIOD=? AND status='Open' AND CREATED_BY='tester'",
                Integer.class, currentPeriod);
        assert assmtCount != null && assmtCount == 1;

        Integer detailCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details d "
                + "JOIN orl_lndscp_assmt a ON d.lndscp_assmt_id=a.id WHERE a.LNDSCP_NUM=1", Integer.class);
        assert detailCount != null && detailCount == 16;
    }

    @Test
    void generate_level2_producesL2GrpAndLocCategories() throws Exception {
        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());

        // L2 rows: BU set, location set
        assertRowCount("category='L2' AND ORL_BU_NM_L2='Technology' AND LOCATION='SG'", 2); // one per risk area
        // grp_l2 rows: BU set, location null
        assertRowCount("category='grp_l2' AND ORL_BU_NM_L2='Operations' AND LOCATION IS NULL", 2);
        // loc rows: BU null, location set
        assertRowCount("category='loc' AND ORL_BU_NM_L2 IS NULL AND LOCATION='CN'", 2);
    }

    @Test
    void generate_level2_resolvesBuHierarchyColumns() throws Exception {
        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());
        // Technology at level 2 → L2='Technology', L3/L4 null
        assertRowCount("ORL_BU_NM_L2='Technology' AND ORL_BU_NM_L3 IS NULL AND ORL_BU_NM_L4 IS NULL AND category='L2'", 4);
    }

    // ── GRC_METRICS + CAL_NET_RISK_RTNG derivation ─────────────────────────────

    @Test
    void generate_grcMetricsAndWorstRating_fromMatchedFacts() throws Exception {
        // Seed facts for (Market Abuse, Technology[L2], SG, L2) for today.
        insertIncFact("Market Abuse", "Technology", null, null, "SG", "L2", "High");
        insertRcsaFact("Market Abuse", "Technology", null, null, "SG", "L2", "Med Low");

        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());

        String grc = jdbc.queryForObject(
                "SELECT GRC_METRICS FROM orl_lndscp_assmt_details d JOIN orl_lndscp_assmt a ON d.lndscp_assmt_id=a.id "
                + "WHERE a.LNDSCP_NUM=1 AND d.RISK_AREA='Market Abuse' AND d.ORL_BU_NM_L2='Technology' "
                + "AND d.LOCATION='SG' AND d.category='L2'", String.class);
        JsonNode root = objectMapper.readTree(grc);
        assert root.has("INC") && root.has("RCSA");
        assert !root.has("INA") && !root.has("KRI");
        assert root.get("INC").get("nrr").asText().equals("High");
        assert root.get("INC").get("inc_is_sinp_count_l3m_mtd").get("count").asInt() == 7;
        assert root.get("INC").get("inc_is_sinp_count_l3m_mtd").get("riskRatingChange").asText().equals("Improved");

        String cal = jdbc.queryForObject(
                "SELECT CAL_NET_RISK_RTNG FROM orl_lndscp_assmt_details d JOIN orl_lndscp_assmt a ON d.lndscp_assmt_id=a.id "
                + "WHERE a.LNDSCP_NUM=1 AND d.RISK_AREA='Market Abuse' AND d.ORL_BU_NM_L2='Technology' "
                + "AND d.LOCATION='SG' AND d.category='L2'", String.class);
        assert "High".equals(cal); // worst of High (INC) and Med Low (RCSA)
    }

    @Test
    void generate_rowWithoutMatchingFact_hasNullGrcAndCal() throws Exception {
        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());
        Integer nullCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details d JOIN orl_lndscp_assmt a ON d.lndscp_assmt_id=a.id "
                + "WHERE a.LNDSCP_NUM=1 AND d.GRC_METRICS IS NULL AND d.CAL_NET_RISK_RTNG IS NULL", Integer.class);
        assert nullCount != null && nullCount == 16; // no facts seeded in this test
    }

    // ── PREV_ASSMT_NUM linkage ──────────────────────────────────────────────────

    @Test
    void generate_linksPreviousMonthAssessment_viaPrevAssmtNum() throws Exception {
        // Previous month's assessment for the same LNDSCP_NUM.
        jdbc.update("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(500,1,?,'Open','seed')",
                previousPeriod);

        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());

        Long prevAssmtNum = jdbc.queryForObject(
                "SELECT PREV_ASSMT_NUM FROM orl_lndscp_assmt WHERE LNDSCP_NUM=1 AND ASSEMT_PERIOD=?",
                Long.class, currentPeriod);
        assert prevAssmtNum != null && prevAssmtNum == 500L;
    }

    @Test
    void generate_noPreviousAssessment_prevAssmtNumNull() throws Exception {
        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());
        Long prevAssmtNum = jdbc.queryForObject(
                "SELECT PREV_ASSMT_NUM FROM orl_lndscp_assmt WHERE LNDSCP_NUM=1 AND ASSEMT_PERIOD=?",
                Long.class, currentPeriod);
        assert prevAssmtNum == null;
    }

    @Test
    void generate_previousAssessmentOfOtherLandscape_isNotLinked() throws Exception {
        // A previous-month assessment exists, but for LNDSCP_NUM=2 — must not be linked to Alpha (id 1).
        jdbc.update("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(501,2,?,'Open','seed')",
                previousPeriod);

        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());

        Long prevAssmtNum = jdbc.queryForObject(
                "SELECT PREV_ASSMT_NUM FROM orl_lndscp_assmt WHERE LNDSCP_NUM=1 AND ASSEMT_PERIOD=?",
                Long.class, currentPeriod);
        assert prevAssmtNum == null;
    }

    // ── Duplicate month ─────────────────────────────────────────────────────────

    @Test
    void generate_twiceSameMonth_returns409() throws Exception {
        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());
        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.message", containsString(currentPeriod)));
    }

    // ── Level 3 category variant ────────────────────────────────────────────────

    @Test
    void generate_level3_usesL3AndGrpL3Categories() throws Exception {
        mvc.perform(post(URL_TPL, "Beta").header("X-EGRC-UserId", "tester"))
           .andExpect(status().isCreated())
           // 1 risk area × ((1 BU × 1 loc) + 1 grp + 1 loc) = 3 rows
           .andExpect(jsonPath("$.data.detailRowCount", is(3)));

        // L3 category row: DTI resolves to L2=Technology, L3=DTI
        Integer l3 = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details d JOIN orl_lndscp_assmt a ON d.lndscp_assmt_id=a.id "
                + "WHERE a.LNDSCP_NUM=2 AND d.category='L3' AND d.ORL_BU_NM_L2='Technology' AND d.ORL_BU_NM_L3='DTI'",
                Integer.class);
        assert l3 != null && l3 == 1;
        assertRowCountForLandscape(2, "category='grp_l3'", 1);
        assertRowCountForLandscape(2, "category='loc'", 1);
    }

    // ── BU not resolvable ───────────────────────────────────────────────────────

    @Test
    void generate_unresolvedBu_stillGeneratesWithNameAtOwnLevel() throws Exception {
        jdbc.execute("DELETE FROM orl_biz_unit WHERE BU_NM='Technology'");
        mvc.perform(post(URL_TPL, "Alpha").header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());
        // Technology unresolved → still present with L2='Technology' (kept at its own level)
        assertRowCount("ORL_BU_NM_L2='Technology' AND category='L2'", 4);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void assertRowCount(String whereClause, int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details d JOIN orl_lndscp_assmt a ON d.lndscp_assmt_id=a.id "
                + "WHERE a.LNDSCP_NUM=1 AND " + whereClause, Integer.class);
        assert count != null && count == expected
                : "Expected " + expected + " rows for [" + whereClause + "] but got " + count;
    }

    private void assertRowCountForLandscape(long lndscpNum, String whereClause, int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details d JOIN orl_lndscp_assmt a ON d.lndscp_assmt_id=a.id "
                + "WHERE a.LNDSCP_NUM=" + lndscpNum + " AND " + whereClause, Integer.class);
        assert count != null && count == expected;
    }

    private void insertIncFact(String riskArea, String l2, String l3, String l4,
                               String loc, String cat, String nrr) {
        jdbc.execute("""
                INSERT INTO inc_fact_orl
                    (inc_is_sinp_count_l3m_mtd,inc_is_mi_count_l3m_mtd,inc_is_gorc_count_l3m_mtd,
                     inc_is_min_reportable_count_l3m_mtd,inc_time_to_detect_sum_l11m_mtd,inc_count_l11m_mtd,
                     biz_dt,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,LOCATION,category,NET_RISK_RTNG)
                VALUES (7,2,3,4,5,6, CURRENT_DATE, %s,%s,%s,%s,%s,%s,%s)
                """.formatted(q(riskArea), q(l2), q(l3), q(l4), q(loc), q(cat), q(nrr)));
    }

    private void insertRcsaFact(String riskArea, String l2, String l3, String l4,
                                String loc, String cat, String nrr) {
        jdbc.execute("""
                INSERT INTO rcsa_fact_orl
                    (rcsa_high_risk_proportion,rcsa_medhigh_risk_proportion,rcsa_medlow_risk_proportion,
                     rcsa_low_risk_proportion,biz_dt,RISK_AREA,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,
                     LOCATION,category,NET_RISK_RTNG)
                VALUES (1,2,3,4, CURRENT_DATE, %s,%s,%s,%s,%s,%s,%s)
                """.formatted(q(riskArea), q(l2), q(l3), q(l4), q(loc), q(cat), q(nrr)));
    }

    /** SQL literal: quoted value, or NULL. */
    private String q(String value) {
        return value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    }
}
