package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.DimensionKey;
import com.dbs.mot.grc.dto.GrcMetric;
import com.dbs.mot.grc.dto.GrcModuleBlock;
import com.dbs.mot.grc.entity.ModuleFact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link GrcMetricsService} — module assembly (nrr as the stored DB value),
 * always-present fully-populated blocks, the target-vs-baseline change derivation, and the
 * KRI/RCSA percentage derivations, against H2.
 */
@SpringBootTest
@ActiveProfiles("test")
class GrcMetricsServiceTest {

    private static final DimensionKey KEY = new DimensionKey("AML Sanctions", "CBG", "", "", "SG");

    /** Every response must name all four modules, in this order. */
    private static final String[] ALL_MODULES = {"RCSA", "INC", "INA", "KRI"};

    @Autowired GrcMetricsService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM inc_fact_orl");
        jdbc.execute("DELETE FROM kri_fact_orl");
        jdbc.execute("DELETE FROM rcsa_fact_orl");
        jdbc.execute("DELETE FROM ina_fact_orl");
    }

    private void insertInc(String bizDt, String nrr, int sinp) {
        jdbc.execute("INSERT INTO inc_fact_orl (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RATING,inc_is_sinp_count_l3m_mtd) "
                + "VALUES(DATE '" + bizDt + "','AML Sanctions','CBG','SG','" + nrr + "'," + sinp + ")");
    }

    private void insertKri(String bizDt, int active, int red, int green) {
        jdbc.execute("INSERT INTO kri_fact_orl (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RATING,"
                + "KRI_ACTIVE_CNT,KRI_RED_CNT,KRI_GREEN_CNT) VALUES(DATE '" + bizDt
                + "','AML Sanctions','CBG','SG','High'," + active + "," + red + "," + green + ")");
    }

    private void insertKriRating(String bizDt, String nrr) {
        jdbc.execute("INSERT INTO kri_fact_orl (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RATING,"
                + "KRI_ACTIVE_CNT,KRI_RED_CNT,KRI_GREEN_CNT) VALUES(DATE '" + bizDt
                + "','AML Sanctions','CBG','SG','" + nrr + "',0,0,0)");
    }

    private void insertRcsa(String bizDt, String highProp, String lowProp) {
        jdbc.execute("INSERT INTO rcsa_fact_orl (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RATING,"
                + "rcsa_high_risk_proportion,rcsa_low_risk_proportion) VALUES(DATE '" + bizDt
                + "','AML Sanctions','CBG','SG','High'," + highProp + "," + lowProp + ")");
    }

    /** Blocks for a business date with no baseline (every change defaults to N.A). */
    private Map<String, GrcModuleBlock> blocksWithNoBaseline(String bizDt) {
        return service.buildBlocks(service.moduleFacts(LocalDate.parse(bizDt), KEY), Map.of());
    }

    /** Finds one metric line in a block by name. */
    private static GrcMetric metric(GrcModuleBlock block, String name) {
        return block.metrics().stream()
                .filter(m -> name.equals(m.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("metric not found: " + name));
    }

    @Test
    void buildBlocks_alwaysReturnsAllModules_fullyPopulated() {
        insertInc("2026-07-15", "High", 7);
        insertKri("2026-07-15", 4, 2, 1);

        Map<String, GrcModuleBlock> blocks = blocksWithNoBaseline("2026-07-15");

        assertThat(blocks.keySet()).containsExactly(ALL_MODULES);

        // INC has a row: nrr is the stored DB value (not a display label) and metrics carry values.
        GrcModuleBlock inc = blocks.get("INC");
        assertThat(inc.nrr()).isEqualTo("High");
        assertThat(metric(inc, "inc_is_sinp_count_l3m_mtd").value()).isEqualTo(7);

        // Modules without a row are still present and fully listed: nrr "N.A", every metric null/N.A.
        GrcModuleBlock rcsa = blocks.get("RCSA");
        assertThat(rcsa).isNotNull();
        assertThat(rcsa.nrr()).isEqualTo("N.A");
        assertThat(rcsa.riskRatingChge()).isEqualTo("N.A");
        assertThat(rcsa.metrics()).isNotEmpty();
        GrcMetric high = metric(rcsa, "combined_count_high_risk");
        assertThat(high.value()).isNull();
        assertThat(high.riskRatingChge()).isEqualTo("N.A");
    }

    @Test
    void buildBlocks_derivesModuleChangeAgainstBaseline() {
        // Target rating 'Low' vs. baseline 'High' → less severe → Improved.
        insertKriRating("2026-07-31", "Low");
        insertKriRating("2026-07-15", "High");
        Map<String, ModuleFact> target = service.moduleFacts(LocalDate.parse("2026-07-31"), KEY);
        Map<String, ModuleFact> baseline = service.moduleFacts(LocalDate.parse("2026-07-15"), KEY);

        GrcModuleBlock kri = service.buildBlocks(target, baseline).get("KRI");

        assertThat(kri.nrr()).isEqualTo("Low");
        assertThat(kri.riskRatingChge()).isEqualTo("Improved");
    }

    @Test
    void buildBlocks_derivesPerMetricChangeAgainstBaseline() {
        // Target KRI red count 5 vs. baseline 2 → Increased; green 1 vs 1 → No change (metric-level
        // equality label — distinct from the module-level "Stable").
        insertKri("2026-07-31", 10, 5, 1);
        insertKri("2026-07-15", 4, 2, 1);
        Map<String, ModuleFact> target = service.moduleFacts(LocalDate.parse("2026-07-31"), KEY);
        Map<String, ModuleFact> baseline = service.moduleFacts(LocalDate.parse("2026-07-15"), KEY);

        GrcModuleBlock kri = service.buildBlocks(target, baseline).get("KRI");

        assertThat(metric(kri, "KRI_RED_CNT").riskRatingChge()).isEqualTo("Increased");
        assertThat(metric(kri, "KRI_GREEN_CNT").riskRatingChge()).isEqualTo("No change");
    }

    @Test
    void buildBlocks_noBaselineFact_isNotApplicable() {
        // Target rating present but no baseline for the module → N.A.
        insertKriRating("2026-07-31", "Low");
        Map<String, ModuleFact> target = service.moduleFacts(LocalDate.parse("2026-07-31"), KEY);

        GrcModuleBlock kri = service.buildBlocks(target, Map.of()).get("KRI");

        assertThat(kri.riskRatingChge()).isEqualTo("N.A");
    }

    @Test
    void buildBlocks_noTargetRow_isFullyDefaulted() {
        // No target facts at all → every module block present, nrr and all changes N.A, values null.
        Map<String, ModuleFact> target = service.moduleFacts(LocalDate.parse("2026-07-31"), KEY);

        Map<String, GrcModuleBlock> blocks = service.buildBlocks(target, Map.of());

        assertThat(blocks.keySet()).containsExactly(ALL_MODULES);
        GrcModuleBlock kri = blocks.get("KRI");
        assertThat(kri.nrr()).isEqualTo("N.A");
        assertThat(kri.riskRatingChge()).isEqualTo("N.A");
        assertThat(metric(kri, "KRI_ACTIVE_CNT").value()).isNull();
    }

    @Test
    void kriProportions_areDerivedAsPercentages() {
        insertKri("2026-07-15", 4, 2, 1);
        GrcModuleBlock kri = blocksWithNoBaseline("2026-07-15").get("KRI");

        // 2 of 4 active → 50.00%; 1 of 4 → 25.00% (percentage, 2 decimal places).
        assertThat(new BigDecimal(metric(kri, "KRI_RED_PROP").value().toString())).isEqualByComparingTo("50.00");
        assertThat(new BigDecimal(metric(kri, "KRI_GREEN_PROP").value().toString())).isEqualByComparingTo("25.00");
    }

    @Test
    void rcsaProportions_areReturnedAsPercentages() {
        // Stored fractions 0.543333 / 0.10 → 54.33% / 10.00% (×100, rounded to 2 dp).
        insertRcsa("2026-07-15", "0.543333", "0.10");
        GrcModuleBlock rcsa = blocksWithNoBaseline("2026-07-15").get("RCSA");

        assertThat(new BigDecimal(metric(rcsa, "rcsa_high_risk_proportion").value().toString()))
                .isEqualByComparingTo("54.33");
        assertThat(new BigDecimal(metric(rcsa, "rcsa_low_risk_proportion").value().toString()))
                .isEqualByComparingTo("10.00");
        // A proportion column with no stored value stays null (not 0%).
        assertThat(metric(rcsa, "rcsa_med_high_proportion").value()).isNull();
    }

    @Test
    void kriProportions_nullWhenActiveCountZero() {
        insertKri("2026-07-15", 0, 0, 0);
        GrcModuleBlock kri = blocksWithNoBaseline("2026-07-15").get("KRI");

        assertThat(metric(kri, "KRI_RED_PROP").value()).isNull();
        assertThat(metric(kri, "KRI_GREEN_PROP").value()).isNull();
    }

    @Test
    void buildBlocks_allModulesDefaultedWhenNoRowsMatch() {
        Map<String, GrcModuleBlock> blocks = blocksWithNoBaseline("2026-07-15");

        assertThat(blocks.keySet()).containsExactly(ALL_MODULES);
        assertThat(blocks.values()).allSatisfy(b -> {
            assertThat(b.nrr()).isEqualTo("N.A");
            assertThat(b.metrics()).isNotEmpty();
            assertThat(b.metrics()).allSatisfy(m -> assertThat(m.value()).isNull());
        });
    }

    @Test
    void moduleFacts_allModulesNullWhenBizDateIsNull() {
        Map<String, ModuleFact> facts = service.moduleFacts(null, KEY);

        assertThat(facts.keySet()).containsExactly(ALL_MODULES);
        assertThat(facts.values()).allSatisfy(f -> assertThat(f).isNull());
    }

    @Test
    void buildBlocks_selectsTheRowOnTheGivenDate() {
        insertInc("2026-07-15", "High", 7);
        insertInc("2026-07-31", "Med Low", 9);

        Map<String, GrcModuleBlock> blocks = blocksWithNoBaseline("2026-07-31");

        GrcModuleBlock inc = blocks.get("INC");
        assertThat(inc.nrr()).isEqualTo("Med Low");
        assertThat(metric(inc, "inc_is_sinp_count_l3m_mtd").value()).isEqualTo(9);
        assertThat(blocks.get("RCSA").nrr()).isEqualTo("N.A");
    }
}
