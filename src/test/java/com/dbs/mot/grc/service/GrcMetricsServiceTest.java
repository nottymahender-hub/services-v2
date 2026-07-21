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
        jdbc.execute("INSERT INTO kri_fact_orl (biz_dt,ORL_RISK_AREA,ORL_BU_NM_L2,LOCATION,NET_RISK_RATING,"
                + "KRI_ACTIVE_CNT,KRI_RED_CNT,KRI_GREEN_CNT) VALUES(DATE '" + bizDt
                + "','AML Sanctions','CBG','SG','High'," + active + "," + red + "," + green + ")");
    }

    @Test
    @SuppressWarnings("unchecked")
    void forBizDate_assemblesPresentModulesOnly() {
        insertInc("2026-07-15", "High", 7);
        insertKri("2026-07-15", 4, 2, 1);

        Map<String, Object> metrics = service.forBizDate(LocalDate.parse("2026-07-15"), KEY);

        assertThat(metrics).containsOnlyKeys("INC", "KRI");
        Map<String, Object> inc = (Map<String, Object>) metrics.get("INC");
        assertThat(inc).containsEntry("nrr", "High").containsEntry("inc_is_sinp_count_l3m_mtd", 7);
    }

    @Test
    void kriProportions_areDerived() {
        insertKri("2026-07-15", 4, 2, 1);
        Map<?, ?> kri = (Map<?, ?>) service.forBizDate(LocalDate.parse("2026-07-15"), KEY).get("KRI");

        assertThat(new BigDecimal(kri.get("KRI_RED_PROP").toString())).isEqualByComparingTo("0.5");
        assertThat(new BigDecimal(kri.get("KRI_GREEN_PROP").toString())).isEqualByComparingTo("0.25");
    }

    @Test
    void kriProportions_nullWhenActiveCountZero() {
        insertKri("2026-07-15", 0, 0, 0);
        Map<?, ?> kri = (Map<?, ?>) service.forBizDate(LocalDate.parse("2026-07-15"), KEY).get("KRI");

        assertThat(kri.get("KRI_RED_PROP")).isNull();
        assertThat(kri.get("KRI_GREEN_PROP")).isNull();
    }

    @Test
    void forBizDate_emptyWhenNoRowsOrNullDate() {
        assertThat(service.forBizDate(LocalDate.parse("2026-07-15"), KEY)).isEmpty();
        assertThat(service.forBizDate(null, KEY)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void live_usesEachModulesOwnLatestRow() {
        insertInc("2026-07-15", "High", 7);
        insertInc("2026-07-31", "Med Low", 9);

        Map<String, Object> inc = (Map<String, Object>) service.live(KEY).get("INC");

        assertThat(inc).containsEntry("inc_is_sinp_count_l3m_mtd", 9);
    }
}
