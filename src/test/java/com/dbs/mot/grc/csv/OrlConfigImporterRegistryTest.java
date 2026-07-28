package com.dbs.mot.grc.csv;

import com.dbs.mot.grc.exception.NotFoundException;
import com.opencsv.CSVWriter;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OrlConfigImporterRegistry} — indexing, lookup and duplicate detection.
 * Uses lightweight stub importers so no Spring context is needed.
 */
class OrlConfigImporterRegistryTest {

    @Test
    void indexesImportersByConfigName_andResolvesThem() {
        OrlConfigImporter a = stub("biz-units");
        OrlConfigImporter b = stub("entity-mstr");
        OrlConfigImporterRegistry registry = new OrlConfigImporterRegistry(List.of(a, b));

        assertThat(registry.knownNames()).containsExactly("biz-units", "entity-mstr");
        assertThat(registry.get("biz-units")).isSameAs(a);
        assertThat(registry.get("entity-mstr")).isSameAs(b);
    }

    @Test
    void get_unknownConfig_throwsNotFoundListingValidNames() {
        OrlConfigImporterRegistry registry = new OrlConfigImporterRegistry(List.of(stub("biz-units")));

        assertThatThrownBy(() -> registry.get("does-not-exist"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("does-not-exist")
                .hasMessageContaining("biz-units");
    }

    @Test
    void duplicateConfigName_failsFast() {
        assertThatThrownBy(() -> new OrlConfigImporterRegistry(List.of(stub("dup"), stub("dup"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dup");
    }

    /** Minimal importer that only reports a config name; other methods are unused here. */
    private OrlConfigImporter stub(String configName) {
        return new OrlConfigImporter() {
            @Override public String configName() { return configName; }
            @Override public String getDownloadFilename() { return configName + ".csv"; }
            @Override public String[] getDownloadHeaders() { return new String[]{"col"}; }
            @Override public int processAndImport(MultipartFile file, String username) { return 0; }
            @Override public void writeDataRows(CSVWriter writer) { /* not exercised */ }
        };
    }
}
