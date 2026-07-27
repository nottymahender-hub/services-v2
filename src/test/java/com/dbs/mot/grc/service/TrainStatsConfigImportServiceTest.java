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
 * Dedicated tests for {@link TrainStatsConfigImportService} against H2 — versioned import,
 * export and download/config metadata.
 */
@SpringBootTest
@ActiveProfiles("test")
class TrainStatsConfigImportServiceTest {

    private static final String TABLE = "train_stats";
    private static final String FIXTURE = "train-stats.csv";

    @Autowired TrainStatsConfigImportService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.execute("DELETE FROM " + TABLE);
    }

    @Test
    void metadata_isExposedForTheController() {
        assertThat(service.configName()).isEqualTo("train-stats");
        assertThat(service.getDownloadFilename()).isEqualTo("train_stats.csv");
        assertThat(service.getDownloadHeaders()).contains("lvl", "module");
    }

    @Test
    void processAndImport_intoEmptyTable_assignsVersionOne_stampingCreatedBy() throws Exception {
        int count = service.processAndImport(fixture(), "analyst");

        assertThat(count).isPositive();
        assertThat(countRows()).isEqualTo(count);
        assertThat(countRowsCreatedBy("analyst")).isEqualTo(count);
        Integer maxVersion = jdbc.queryForObject("SELECT MAX(config_version) FROM " + TABLE, Integer.class);
        assertThat(maxVersion).isEqualTo(1);
    }

    @Test
    void writeDataRows_afterImport_emitsLatestPerGroup() throws Exception {
        service.processAndImport(fixture(), "u");

        StringWriter sink = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sink)) {
            service.writeDataRows(writer);
        }
        assertThat(sink.toString().lines().count()).isEqualTo(countRows());
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
