package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.DimensionKey;
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
 * Integration tests for {@link GrcMetricsService} — module assembly, KRI derivation and
 * live latest-per-module resolution, against H2.
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
        jdbc.execute("INSERT INTO inc_fact_orl (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RTNG,inc_is_sinp_count_l3m_mtd) "
                + "VALUES(DATE '" + bizDt + "','AML Sanctions','CBG','SG','" + nrr + "'," + sinp + ")");
    }

    private void insertKri(String bizDt, int active, int red, int green) {
        jdbc.execute("INSERT INTO kri_fact_orl (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RTNG,"
                + "KRI_ACTIVE_CNT,KRI_RED_CNT,KRI_GREEN_CNT) VALUES(DATE '" + bizDt
                + "','AML Sanctions','CBG','SG','High'," + active + "," + red + "," + green + ")");
    }

    private void insertRcsa(String bizDt, String highProp, String lowProp) {
        jdbc.execute("INSERT INTO rcsa_fact_orl (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RTNG,"
                + "rcsa_high_risk_proportion,rcsa_low_risk_proportion) VALUES(DATE '" + bizDt
                + "','AML Sanctions','CBG','SG','High'," + highProp + "," + lowProp + ")");
    }

    @Test
    @SuppressWarnings("unchecked")
    void forBizDate_namesAllModules_populatingOnlyThoseWithRows() {
        insertInc("2026-07-15", "High", 7);
        insertKri("2026-07-15", 4, 2, 1);

        Map<String, Object> metrics = service.forBizDate(LocalDate.parse("2026-07-15"), KEY);

        assertThat(metrics.keySet()).containsExactly(ALL_MODULES);
        Map<String, Object> inc = (Map<String, Object>) metrics.get("INC");
        assertThat(inc).containsEntry("nrr", "High").containsEntry("inc_is_sinp_count_l3m_mtd", 7);
        assertThat(metrics.get("KRI")).isNotNull();
        // Modules without a snapshot row are still named, mapped to null.
        assertThat(metrics.get("RCSA")).isNull();
        assertThat(metrics.get("INA")).isNull();
    }

    @Test
    void kriProportions_areDerivedAsPercentages() {
        insertKri("2026-07-15", 4, 2, 1);
        Map<?, ?> kri = (Map<?, ?>) service.forBizDate(LocalDate.parse("2026-07-15"), KEY).get("KRI");

        // 2 of 4 active → 50.00%; 1 of 4 → 25.00% (percentage, 2 decimal places).
        assertThat(new BigDecimal(kri.get("KRI_RED_PROP").toString())).isEqualByComparingTo("50.00");
        assertThat(new BigDecimal(kri.get("KRI_GREEN_PROP").toString())).isEqualByComparingTo("25.00");
    }

    @Test
    void rcsaProportions_areReturnedAsPercentages() {
        // Stored fractions 0.543333 / 0.10 → 54.33% / 10.00% (×100, rounded to 2 dp).
        insertRcsa("2026-07-15", "0.543333", "0.10");
        Map<?, ?> rcsa = (Map<?, ?>) service.forBizDate(LocalDate.parse("2026-07-15"), KEY).get("RCSA");

        assertThat(new BigDecimal(rcsa.get("rcsa_high_risk_proportion").toString()))
                .isEqualByComparingTo("54.33");
        assertThat(new BigDecimal(rcsa.get("rcsa_low_risk_proportion").toString()))
                .isEqualByComparingTo("10.00");
        // A proportion column with no stored value stays null (not 0%).
        assertThat(rcsa.get("rcsa_med_high_proportion")).isNull();
    }

    @Test
    void kriProportions_nullWhenActiveCountZero() {
        insertKri("2026-07-15", 0, 0, 0);
        Map<?, ?> kri = (Map<?, ?>) service.forBizDate(LocalDate.parse("2026-07-15"), KEY).get("KRI");

        assertThat(kri.get("KRI_RED_PROP")).isNull();
        assertThat(kri.get("KRI_GREEN_PROP")).isNull();
    }

    @Test
    void forBizDate_allModulesNullWhenNoRowsMatch() {
        Map<String, Object> metrics = service.forBizDate(LocalDate.parse("2026-07-15"), KEY);

        assertThat(metrics.keySet()).containsExactly(ALL_MODULES);
        assertThat(metrics).containsOnlyKeys(ALL_MODULES).containsValue(null);
        assertThat(metrics.values()).containsOnlyNulls();
    }

    @Test
    void forBizDate_allModulesNullWhenBizDateIsNull() {
        Map<String, Object> metrics = service.forBizDate(null, KEY);

        assertThat(metrics.keySet()).containsExactly(ALL_MODULES);
        assertThat(metrics.values()).containsOnlyNulls();
    }

    @Test
    @SuppressWarnings("unchecked")
    void forBizDate_selectsTheRowOnTheGivenDate() {
        // The "live" drill-down now resolves the latest business date and calls forBizDate with it,
        // so a plain by-date lookup must select exactly the row on that date.
        insertInc("2026-07-15", "High", 7);
        insertInc("2026-07-31", "Med Low", 9);

        Map<String, Object> metrics = service.forBizDate(LocalDate.parse("2026-07-31"), KEY);

        assertThat(metrics.keySet()).containsExactly(ALL_MODULES);
        Map<String, Object> inc = (Map<String, Object>) metrics.get("INC");
        assertThat(inc).containsEntry("inc_is_sinp_count_l3m_mtd", 9);
        assertThat(metrics.get("RCSA")).isNull();
    }
}
