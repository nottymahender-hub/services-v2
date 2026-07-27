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
 * Dedicated tests for {@link FeatureScoreBandConfigImportService} against H2.
 *
 * <p>Verifies the versioned import ({@code MAX+1} per natural-key group, starting at 1 for an
 * empty table), the latest-per-group export, and the download/config metadata.
 */
@SpringBootTest
@ActiveProfiles("test")
class FeatureScoreBandConfigImportServiceTest {

    private static final String TABLE = "feature_score_band";
    private static final String FIXTURE = "feature-score-band.csv";

    @Autowired FeatureScoreBandConfigImportService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.execute("DELETE FROM " + TABLE);
    }

    @Test
    void metadata_isExposedForTheController() {
        assertThat(service.configName()).isEqualTo("feature-score-band");
        assertThat(service.getDownloadFilename()).isEqualTo("feature_score_band.csv");
        assertThat(service.getDownloadHeaders()).contains("feature_name", "module", "config_version");
    }

    @Test
    void processAndImport_intoEmptyTable_assignsVersionOne_stampingCreatedBy() throws Exception {
        int count = service.processAndImport(fixture(), "analyst");

        assertThat(count).isPositive();
        assertThat(countRows()).isEqualTo(count);
        assertThat(countRowsCreatedBy("analyst")).isEqualTo(count);
        // First import into an empty table → every group gets config_version = 1.
        Integer maxVersion = jdbc.queryForObject("SELECT MAX(config_version) FROM " + TABLE, Integer.class);
        assertThat(maxVersion).isEqualTo(1);
    }

    @Test
    void processAndImport_secondUpload_bumpsVersionToTwo() throws Exception {
        service.processAndImport(fixture(), "u");
        service.processAndImport(fixture(), "u");

        // Re-uploading the same natural-key groups bumps them to MAX+1 = 2.
        Integer maxVersion = jdbc.queryForObject("SELECT MAX(config_version) FROM " + TABLE, Integer.class);
        assertThat(maxVersion).isEqualTo(2);
    }

    @Test
    void writeDataRows_returnsOnlyLatestVersionPerGroup() throws Exception {
        service.processAndImport(fixture(), "u");
        int rowsAfterFirst = countRows();
        service.processAndImport(fixture(), "u");

        StringWriter sink = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sink)) {
            service.writeDataRows(writer);
        }
        // Two versions stored per group, but the export reduces to the latest — one line per group,
        // matching the single-version row count from the first import.
        assertThat(sink.toString().lines().count()).isEqualTo(rowsAfterFirst);
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
