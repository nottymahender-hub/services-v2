package com.dbs.mot.grc.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-style tests for {@link LandscapeDimensionConfigImportService#resolveVersion(List)} — the
 * guard branches that the endpoint tests cannot easily reach. Lives in the same package so it can
 * call the package-private helper. The MAX+1 bump path is covered end-to-end by the handler test.
 */
@SpringBootTest
@ActiveProfiles("test")
class LandscapeDimensionConfigImportServiceTest {

    @Autowired LandscapeDimensionConfigImportService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        // Delete FK children (assessments and their descendants) before the parent config rows,
        // so this test is robust to assessment rows left behind by other @SpringBootTest classes
        // that share the same H2 instance (Spring context tests are not rolled back).
        jdbc.execute("DELETE FROM orl_lndscp_callout_comment_hist");
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
    }

    @Test
    void resolveVersion_isOne_whenNoConfigIdsSupplied() {
        assertThat(service.resolveVersion(List.of())).isEqualTo(1);
        assertThat(service.resolveVersion(null)).isEqualTo(1);
    }

    @Test
    void resolveVersion_isOne_whenConfigIdIsUnknown() {
        assertThat(service.resolveVersion(List.of("NOT-STORED"))).isEqualTo(1);
    }
}
