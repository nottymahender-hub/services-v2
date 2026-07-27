package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.opencsv.CSVWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated tests for {@link OrlConfigurationService} against H2.
 *
 * <p>The service only orchestrates: it runs the table-specific import and records the upload-audit
 * row in one transaction. A stub {@link OrlConfigImporter} isolates the orchestration from any real
 * table, so these tests assert the wiring — the import runs once, its count is returned, and an
 * audit row is committed with the importer's config name, the original filename and the operator.
 * The real audit insert is {@code MANDATORY}, which also proves {@code importConfig} supplies the
 * surrounding transaction.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrlConfigurationServiceTest {

    private static final String AUDIT_TABLE = "orl_static_data_maintianance_csv_upload_audit";

    @Autowired OrlConfigurationService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.execute("DELETE FROM " + AUDIT_TABLE);
    }

    @Test
    void importConfig_runsImportOnce_returnsCount_andRecordsAudit() {
        AtomicInteger imports = new AtomicInteger();
        OrlConfigImporter importer = stubImporter("biz-units", () -> {
            imports.incrementAndGet();
            return 42;
        });

        int count = service.importConfig(importer, file("a,b\n1,2\n"), "analyst", "upload.csv");

        assertThat(count).isEqualTo(42);
        assertThat(imports.get()).isEqualTo(1);
        assertThat(countAudit()).isEqualTo(1);

        // Column names are case-insensitive in SQL, sidestepping H2's identifier-casing rules.
        assertThat(auditString("table_name")).isEqualTo("biz-units");
        assertThat(auditString("file_name")).isEqualTo("upload.csv");
        assertThat(auditString("uploaded_by")).isEqualTo("analyst");
        assertThat(auditString("status")).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT row_count FROM " + AUDIT_TABLE, Integer.class)).isEqualTo(42);
    }

    private String auditString(String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + AUDIT_TABLE, String.class);
    }

    @Test
    void importConfig_importFailure_rollsBackAudit() {
        OrlConfigImporter failing = stubImporter("entity-mstr", () -> {
            throw new IllegalStateException("boom");
        });

        try {
            service.importConfig(failing, file("x\n"), "u", "bad.csv");
        } catch (IllegalStateException expected) {
            // The import threw; the audit insert must not have committed.
        }

        assertThat(countAudit()).isZero();
    }

    private int countAudit() {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM " + AUDIT_TABLE, Integer.class));
    }

    private MultipartFile file(String content) {
        return new MockMultipartFile("file", "upload.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    /** Minimal in-memory importer that records nothing but a call count via the supplied action. */
    private OrlConfigImporter stubImporter(String configName, java.util.function.IntSupplier importAction) {
        return new OrlConfigImporter() {
            @Override
            public String configName() {
                return configName;
            }

            @Override
            public String getDownloadFilename() {
                return configName + ".csv";
            }

            @Override
            public String[] getDownloadHeaders() {
                return new String[]{"col"};
            }

            @Override
            public int processAndImport(MultipartFile file, String username) {
                return importAction.getAsInt();
            }

            @Override
            public void writeDataRows(CSVWriter writer) {
                // no-op: export is not exercised by the orchestrator
            }
        };
    }
}
