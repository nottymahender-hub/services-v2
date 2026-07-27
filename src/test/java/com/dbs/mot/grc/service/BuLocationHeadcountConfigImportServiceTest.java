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
 * Dedicated tests for {@link BuLocationHeadcountConfigImportService} against H2 — the composite-key
 * upsert (BU path + location), the export and the download/config metadata.
 */
@SpringBootTest
@ActiveProfiles("test")
class BuLocationHeadcountConfigImportServiceTest {

    private static final String TABLE = "orl_bu_loctn_headcount";
    private static final String FIXTURE = "bu-loctn-headcount.csv";

    @Autowired BuLocationHeadcountConfigImportService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.execute("DELETE FROM " + TABLE);
    }

    @Test
    void metadata_isExposedForTheController() {
        assertThat(service.configName()).isEqualTo("bu-loctn-headcount");
        assertThat(service.getDownloadFilename()).isEqualTo("orl_bu_loctn_headcount.csv");
        assertThat(service.getDownloadHeaders()).contains("ORL_BU_NM_L2", "location", "headcount");
    }

    @Test
    void processAndImport_intoEmptyTable_insertsRows_stampingCreatedBy() throws Exception {
        int count = service.processAndImport(fixture(), "analyst");

        assertThat(count).isPositive();
        int rows = countRows();
        assertThat(rows).isPositive();
        // Empty table → every de-duplicated composite key is an insert stamped with the operator.
        assertThat(countRowsCreatedBy("analyst")).isEqualTo(rows);
    }

    @Test
    void processAndImport_reupload_updatesInsteadOfDuplicating() throws Exception {
        service.processAndImport(fixture(), "first");
        int rowsAfterFirst = countRows();

        service.processAndImport(fixture(), "second");

        // Upsert keyed on (BU path + location): re-uploading the same keys must not grow the
        // table, and the update path stamps UPDATED_BY while carrying CREATED_BY forward.
        assertThat(countRows()).isEqualTo(rowsAfterFirst);
        assertThat(countRowsUpdatedBy("second")).isEqualTo(rowsAfterFirst);
    }

    @Test
    void writeDataRows_afterImport_emitsOneLinePerRow() throws Exception {
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

    private int countRowsUpdatedBy(String user) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE UPDATED_BY = ?", Integer.class, user));
    }

    private MockMultipartFile fixture() throws Exception {
        try (InputStream is = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream(FIXTURE), FIXTURE + " missing from test resources")) {
            return new MockMultipartFile("file", FIXTURE, "text/csv", is.readAllBytes());
        }
    }
}
