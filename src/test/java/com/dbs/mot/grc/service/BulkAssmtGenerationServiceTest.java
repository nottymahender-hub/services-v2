package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.AssmtGenerationResult;
import com.dbs.mot.grc.dto.AssmtGenerationStatus;
import com.dbs.mot.grc.dto.BulkAssmtGenerationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated tests for {@link BulkAssmtGenerationService} against H2 — grouping/filtering active
 * landscapes by effectivity, and translating {@code generateForDim}'s three skip conditions
 * (ambiguous config, already exists, no fact data) plus the happy path into per-landscape results
 * and the generated/skipped counts.
 *
 * <h3>Seed data</h3>
 * <ul>
 *   <li>'Alpha' (id 1, level 2): 2 risk areas x ((2 BU x 2 loc) + 2 grp + 2 loc) = 16 rows</li>
 *   <li>'Beta'  (id 2, level 3): 1 risk area x ((1 BU x 1 loc) + 1 grp + 1 loc) = 3 rows</li>
 *   <li>One {@code fact_orl} row within the reported month (July 2026) so the happy-path tests
 *       generate by default; individual tests remove it to exercise {@code SKIPPED_NO_DATA}.</li>
 * </ul>
 *
 * <p>As-of date is fixed ({@code 2026-08-15}), matching {@link LandscapeAssmtGenerationServiceTest},
 * since neither service depends on the wall-clock date (the future-date guard lives in the controller).
 */
@SpringBootTest
@ActiveProfiles("test")
class BulkAssmtGenerationServiceTest {

    // As-of 2026-08-15 -> reports July 2026 (M-1).
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 15);
    private static final String REPORTED_PERIOD = "July 2026";

    @Autowired BulkAssmtGenerationService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        jdbc.execute("DELETE FROM orl_biz_unit");
        jdbc.execute("DELETE FROM fact_orl");

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

        jdbc.execute("INSERT INTO fact_orl (biz_dt, RISK_AREA, category, CAL_NET_RISK_RTNG) "
                + "VALUES (DATE '2026-07-15', 'Any', 'L2', 'Low')");
    }

    // ── Grouping / filtering ────────────────────────────────────────────────────

    @Test
    void generateForAllActiveLandscapes_generatesForEachActiveEffectiveLandscape() {
        BulkAssmtGenerationResponse response = service.generateForAllActiveLandscapes(AS_OF_DATE, "tester");

        assertThat(response.getTotalLandscapes()).isEqualTo(2);
        assertThat(response.getGenerated()).isEqualTo(2);
        assertThat(response.getSkipped()).isEqualTo(0);

        AssmtGenerationResult alpha = resultFor(response, "Alpha");
        assertThat(alpha.getStatus()).isEqualTo(AssmtGenerationStatus.GENERATED);
        assertThat(alpha.getLndscpNum()).isEqualTo(1L);
        assertThat(alpha.getLndscpAssmtId()).isNotNull();
        assertThat(alpha.getDetailRowCount()).isEqualTo(16);

        AssmtGenerationResult beta = resultFor(response, "Beta");
        assertThat(beta.getStatus()).isEqualTo(AssmtGenerationStatus.GENERATED);
        assertThat(beta.getDetailRowCount()).isEqualTo(3);
    }

    @Test
    void generateForAllActiveLandscapes_noActiveLandscapes_returnsEmptyResponse() {
        jdbc.execute("DELETE FROM orl_lndscp_dim");

        BulkAssmtGenerationResponse response = service.generateForAllActiveLandscapes(AS_OF_DATE, "tester");

        assertThat(response.getTotalLandscapes()).isEqualTo(0);
        assertThat(response.getGenerated()).isEqualTo(0);
        assertThat(response.getSkipped()).isEqualTo(0);
        assertThat(response.getResults()).isEmpty();
    }

    @Test
    void generateForAllActiveLandscapes_excludesDeactivatedConfig() {
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,STATUS,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(3,'CFG003','Gamma',DATE '2024-01-01',1,'DEACTIVATED','[]','SG','seed')
                """);

        BulkAssmtGenerationResponse response = service.generateForAllActiveLandscapes(AS_OF_DATE, "tester");

        assertThat(response.getTotalLandscapes()).isEqualTo(2);
        assertThat(response.getResults()).extracting(AssmtGenerationResult::getLndscpNm)
                .containsExactlyInAnyOrder("Alpha", "Beta");
    }

    @Test
    void generateForAllActiveLandscapes_excludesConfigOutsideEffectiveWindow() {
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,EFFECT_END_DT,VERSION,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(4,'CFG004','Delta',DATE '2020-01-01',DATE '2020-12-31',1,'[]','SG','seed')
                """);

        BulkAssmtGenerationResponse response = service.generateForAllActiveLandscapes(AS_OF_DATE, "tester");

        assertThat(response.getTotalLandscapes()).isEqualTo(2);
        assertThat(response.getResults()).extracting(AssmtGenerationResult::getLndscpNm)
                .doesNotContain("Delta");
    }

    // ── Skip conditions ─────────────────────────────────────────────────────────

    @Test
    void generateForAllActiveLandscapes_ambiguousConfig_skipsThatLandscapeOnly() {
        // A second ACTIVE, currently-effective config for 'Alpha' makes it ambiguous; Beta unaffected.
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(6,'CFG001','Alpha',DATE '2025-01-01',2,'[]','SG','seed')
                """);

        BulkAssmtGenerationResponse response = service.generateForAllActiveLandscapes(AS_OF_DATE, "tester");

        assertThat(response.getGenerated()).isEqualTo(1);
        assertThat(response.getSkipped()).isEqualTo(1);

        AssmtGenerationResult alpha = resultFor(response, "Alpha");
        assertThat(alpha.getStatus()).isEqualTo(AssmtGenerationStatus.SKIPPED_AMBIGUOUS_CONFIG);
        assertThat(alpha.getLndscpNum()).isNull();
        assertThat(alpha.getMessage()).contains("Multiple active configs");

        assertThat(resultFor(response, "Beta").getStatus()).isEqualTo(AssmtGenerationStatus.GENERATED);
    }

    @Test
    void generateForAllActiveLandscapes_alreadyGenerated_skipsWithAlreadyExistsStatus() {
        service.generateForAllActiveLandscapes(AS_OF_DATE, "tester");

        BulkAssmtGenerationResponse second = service.generateForAllActiveLandscapes(AS_OF_DATE, "tester");

        assertThat(second.getGenerated()).isEqualTo(0);
        assertThat(second.getSkipped()).isEqualTo(2);
        assertThat(second.getResults()).extracting(AssmtGenerationResult::getStatus)
                .containsOnly(AssmtGenerationStatus.SKIPPED_ALREADY_EXISTS);
    }

    @Test
    void generateForAllActiveLandscapes_noFactData_skipsWithNoDataStatus() {
        jdbc.execute("DELETE FROM fact_orl");

        BulkAssmtGenerationResponse response = service.generateForAllActiveLandscapes(AS_OF_DATE, "tester");

        assertThat(response.getGenerated()).isEqualTo(0);
        assertThat(response.getSkipped()).isEqualTo(2);
        assertThat(response.getResults()).extracting(AssmtGenerationResult::getStatus)
                .containsOnly(AssmtGenerationStatus.SKIPPED_NO_DATA);
        assertThat(response.getResults()).extracting(AssmtGenerationResult::getMessage)
                .allMatch(msg -> msg.contains("No fact_orl data") && msg.contains(REPORTED_PERIOD));
    }

    @Test
    void generateForAllActiveLandscapes_mixedOutcomes_countsGeneratedAndSkippedCorrectly() {
        // Pre-existing assessment for Beta's reported period forces its skip; Alpha has none, so it
        // generates normally — proving one landscape's skip doesn't affect another's outcome or counts.
        jdbc.update("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(900,2,?,'Open','seed')",
                REPORTED_PERIOD);

        BulkAssmtGenerationResponse response = service.generateForAllActiveLandscapes(AS_OF_DATE, "tester");

        assertThat(response.getTotalLandscapes()).isEqualTo(2);
        assertThat(response.getGenerated()).isEqualTo(1);
        assertThat(response.getSkipped()).isEqualTo(1);
        assertThat(resultFor(response, "Alpha").getStatus()).isEqualTo(AssmtGenerationStatus.GENERATED);
        assertThat(resultFor(response, "Beta").getStatus()).isEqualTo(AssmtGenerationStatus.SKIPPED_ALREADY_EXISTS);
    }

    private AssmtGenerationResult resultFor(BulkAssmtGenerationResponse response, String lndscpNm) {
        return response.getResults().stream()
                .filter(r -> r.getLndscpNm().equals(lndscpNm))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No result for landscape '" + lndscpNm + "'"));
    }
}
