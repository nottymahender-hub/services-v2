package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.LandscapeAssmtSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated tests for {@link LandscapeAssmtService} against H2 — the listing projection, the
 * landscape-name join, the last-modified fallback and the query's most-recently-modified ordering.
 */
@SpringBootTest
@ActiveProfiles("test")
class LandscapeAssmtServiceTest {

    @Autowired LandscapeAssmtService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,'[]',2,'SG','seed')
                """);
        // Assmt 20: only CREATE_DT_TM (no update) → last-modified falls back to create fields.
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY,CREATE_DT_TM) "
                + "VALUES(20,1,'Q1-2024','Open','creator',TIMESTAMP '2024-01-01 09:00:00')");
        // Assmt 21: has UPDATE_DT_TM later than assmt 20's create → must sort first.
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY,CREATE_DT_TM,UPDATE_DT_TM,UPDATED_BY) "
                + "VALUES(21,1,'Q2-2024','Draft','creator',TIMESTAMP '2024-01-01 09:00:00',TIMESTAMP '2024-03-01 12:00:00','editor')");
    }

    @Test
    void fetchAll_emptyTable_returnsEmptyList() {
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        assertThat(service.fetchAll()).isEmpty();
    }

    @Test
    void fetchAll_returnsSummariesOrderedMostRecentlyModifiedFirst() {
        List<LandscapeAssmtSummary> summaries = service.fetchAll();

        assertThat(summaries).hasSize(2);
        // Assmt 21 was modified 2024-03-01; assmt 20 only created 2024-01-01 → 21 sorts first.
        assertThat(summaries).extracting(LandscapeAssmtSummary::getLandscapeAssmtId)
                .containsExactly(21L, 20L);
        assertThat(summaries).allSatisfy(s -> assertThat(s.getLandscapeName()).isEqualTo("Alpha"));
    }

    @Test
    void fetchAll_lastModified_usesUpdateFieldsWhenPresent() {
        LandscapeAssmtSummary updated = summaryFor(21L);
        assertThat(updated.getLastModifiedBy()).isEqualTo("editor");
        assertThat(updated.getLastModifiedOn()).isEqualTo("2024-03-01 12:00:00");
    }

    @Test
    void fetchAll_lastModified_fallsBackToCreateFieldsWhenNoUpdate() {
        LandscapeAssmtSummary created = summaryFor(20L);
        assertThat(created.getLastModifiedBy()).isEqualTo("creator");
        assertThat(created.getLastModifiedOn()).isEqualTo("2024-01-01 09:00:00");
    }

    private LandscapeAssmtSummary summaryFor(Long id) {
        return service.fetchAll().stream()
                .filter(s -> id.equals(s.getLandscapeAssmtId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no summary for id " + id));
    }
}
