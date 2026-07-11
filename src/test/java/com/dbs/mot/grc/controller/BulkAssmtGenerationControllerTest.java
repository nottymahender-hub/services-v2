package com.dbs.mot.grc.controller;

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

    private final String currentPeriod = YearMonth.now().format(PERIOD_FMT);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

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

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '{"Market Abuse":["OR","LCS"],"AML, CFT and Sanctions":["OR"]}',
                    'Technology,Operations',2,'SG,CN','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(2,'CFG002','Beta',DATE '2024-01-01',1,
                    '{"Market Abuse":["OR"]}','DTI',3,'SG','seed')
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
                Integer.class, currentPeriod);
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
                    '{"Market Abuse":["OR"]}','SG','seed')
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
                    '{"Market Abuse":["OR"]}','SG','seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,EFFECT_END_DT,VERSION,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(4,'CFG004','Delta',DATE '2020-01-01',DATE '2020-12-31',1,
                    '{"Market Abuse":["OR"]}','SG','seed')
                """);

        mvc.perform(post(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data.totalLandscapes", is(2)))
           .andExpect(jsonPath("$.data.results[*].lndscpNm",
                   containsInAnyOrder("Alpha", "Beta")));
    }
}
