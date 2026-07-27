package com.dbs.mot.grc.service;

import com.opencsv.CSVWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated tests for {@link RiskTypeRiskAreaMapConfigImportService} against H2.
 *
 * <p>Exercises the importer directly (bypassing the upload endpoint): the append-only
 * {@code processAndImport} happy path, the export {@code writeDataRows}, and the static
 * download/config metadata the controller relies on.
 */
@SpringBootTest
@ActiveProfiles("test")
class RiskTypeRiskAreaMapConfigImportServiceTest {

    private static final String TABLE = "orl_risk_type_risk_area_map";
    private static final String FIXTURE = "risk-type-risk-area-maps.csv";

    @Autowired RiskTypeRiskAreaMapConfigImportService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.execute("DELETE FROM " + TABLE);
    }

    @Test
    void metadata_isExposedForTheController() {
        assertThat(service.configName()).isEqualTo("risk-type-risk-area-maps");
        assertThat(service.getDownloadFilename()).isEqualTo("orl_risk_type_risk_area_map.csv");
        assertThat(service.getDownloadHeaders()).contains("RISK_AREA", "RISK_TYPE_L4_NUM", "RISK_TYPE_L4_NM");
    }

    @Test
    void processAndImport_persistsEveryRow_stampingCreatedBy() throws Exception {
        int count = service.processAndImport(fixture(), "analyst");

        assertThat(count).isPositive();
        assertThat(countRows()).isEqualTo(count);
        assertThat(countRowsCreatedBy("analyst")).isEqualTo(count);
    }

    @Test
    void writeDataRows_afterImport_emitsThePersistedRows() throws Exception {
        service.processAndImport(fixture(), "u");

        StringWriter sink = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sink)) {
            service.writeDataRows(writer);
        }
        // One data line per stored row (no header written by the importer itself).
        assertThat(sink.toString().lines().count()).isEqualTo(countRows());
    }

    @Test
    void writeDataRows_emptyTable_emitsNothing() {
        StringWriter sink = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sink)) {
            service.writeDataRows(writer);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        assertThat(sink.toString()).isEmpty();
    }

    private int countRows() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class));
    }

    private int countRowsCreatedBy(String user) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE CREATED_BY = ?", Integer.class, user));
    }

    private MockMultipartFile fixture() throws Exception {
        try (InputStream is = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream(FIXTURE), FIXTURE + " missing from test resources")) {
            return new MockMultipartFile("file", FIXTURE, "text/csv", is.readAllBytes());
        }
    }
}
