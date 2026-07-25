package com.dbs.mot.grc.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Integration tests for {@link ConfigVersionResolver} against H2 — the {@code MAX + 1} rule,
 * per-group independence, and the validity of every {@link ConfigTable}'s static SQL.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConfigVersionResolverTest {

    @Autowired ConfigVersionResolver resolver;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        jdbc.execute("DELETE FROM feature_score_band");
        jdbc.execute("DELETE FROM train_stats");
        jdbc.execute("DELETE FROM net_risk_band");
    }

    /** Inserts one {@code feature_score_band} row at the given version for a known group. */
    private void insertFeatureScoreBand(String featureName, int bin, String module, int version) {
        jdbc.update("INSERT INTO feature_score_band "
                        + "(config_version, feature_bin, feature_name, range_low, range_high, score, module) "
                        + "VALUES (?, ?, ?, 0, 1, 10, ?)",
                version, bin, featureName, module);
    }

    @SafeVarargs
    private Set<List<String>> keys(List<String>... groupKeys) {
        return Set.of(groupKeys);
    }

    @Test
    void newGroup_startsAtVersionOne() {
        Map<String, Integer> resolved = resolver.resolveNextVersions(
                ConfigTable.FEATURE_SCORE_BAND, keys(List.of("inc_is_sinp_count_l3m_mtd", "1", "INC")));

        assertThat(resolved)
                .containsExactly(Map.entry(
                        ConfigVersionResolver.groupKey("inc_is_sinp_count_l3m_mtd", "1", "INC"), 1));
    }

    @Test
    void existingGroup_advancesToMaxPlusOne() {
        insertFeatureScoreBand("inc_is_sinp_count_l3m_mtd", 1, "INC", 1);
        insertFeatureScoreBand("inc_is_sinp_count_l3m_mtd", 1, "INC", 4);

        Map<String, Integer> resolved = resolver.resolveNextVersions(
                ConfigTable.FEATURE_SCORE_BAND, keys(List.of("inc_is_sinp_count_l3m_mtd", "1", "INC")));

        assertThat(resolved)
                .containsEntry(ConfigVersionResolver.groupKey("inc_is_sinp_count_l3m_mtd", "1", "INC"), 5);
    }

    @Test
    void eachGroupIsVersionedIndependently() {
        // Only the INC group exists, and only at version 2.
        insertFeatureScoreBand("inc_is_sinp_count_l3m_mtd", 1, "INC", 2);

        Map<String, Integer> resolved = resolver.resolveNextVersions(
                ConfigTable.FEATURE_SCORE_BAND,
                keys(List.of("inc_is_sinp_count_l3m_mtd", "1", "INC"),
                     List.of("inc_is_mi_count_l3m_mtd", "2", "INC")));

        assertThat(resolved)
                .containsEntry(ConfigVersionResolver.groupKey("inc_is_sinp_count_l3m_mtd", "1", "INC"), 3)
                .containsEntry(ConfigVersionResolver.groupKey("inc_is_mi_count_l3m_mtd", "2", "INC"), 1);
    }

    @Test
    void differentModulesAreDifferentGroups() {
        insertFeatureScoreBand("inc_is_sinp_count_l3m_mtd", 1, "INC", 7);

        Map<String, Integer> resolved = resolver.resolveNextVersions(
                ConfigTable.FEATURE_SCORE_BAND,
                keys(List.of("inc_is_sinp_count_l3m_mtd", "1", "TPRM")));

        // The TPRM group has never been uploaded, so the INC group's version 7 is irrelevant.
        assertThat(resolved)
                .containsEntry(ConfigVersionResolver.groupKey("inc_is_sinp_count_l3m_mtd", "1", "TPRM"), 1);
    }

    @Test
    void emptyBatch_resolvesNothing() {
        assertThat(resolver.resolveNextVersions(ConfigTable.FEATURE_SCORE_BAND, Set.of())).isEmpty();
    }

    @Test
    void groupKey_joinsValuesWithoutCollidingOnAdjacentValues() {
        // A plain concatenation would make ("ab","c") and ("a","bc") the same key.
        assertThat(ConfigVersionResolver.groupKey("ab", "c"))
                .isNotEqualTo(ConfigVersionResolver.groupKey("a", "bc"));
    }

    /**
     * Guards every {@link ConfigTable}'s hand-written static SQL by executing it: the database
     * rejects an unknown table or column, so a typo in a constant fails here rather than at
     * upload time. Reading the returned columns is covered by the {@code MAX + 1} tests above
     * (and, for the other two tables, by their CSV handler tests).
     */
    @ParameterizedTest
    @EnumSource(ConfigTable.class)
    void everyConfigTableSqlIsValidAgainstTheSchema(ConfigTable table) {
        assertThat(table.groupByColumns()).isNotEmpty();
        assertThatCode(() -> jdbc.query(table.maxVersionSql(), rs -> { /* execution is the assertion */ }))
                .doesNotThrowAnyException();
    }
}
