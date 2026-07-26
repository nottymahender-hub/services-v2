package com.dbs.mot.grc.util;

import com.dbs.mot.grc.util.ConfigVersionResolver.GroupMax;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link ConfigVersionResolver} — the {@code MAX + 1} rule, per-group
 * independence and collision-free key joining. No database is involved: callers now fetch the
 * per-group maxima through repositories and pass them in.
 */
class ConfigVersionResolverTest {

    private final ConfigVersionResolver resolver = new ConfigVersionResolver();

    @Test
    void newGroup_startsAtVersionOne() {
        Map<String, Integer> resolved = resolver.resolveNextVersions(
                List.of(),
                Set.of(List.of("inc_is_sinp_count_l3m_mtd", "1", "INC")));

        assertThat(resolved).containsExactly(Map.entry(
                ConfigVersionResolver.groupKey("inc_is_sinp_count_l3m_mtd", "1", "INC"), 1));
    }

    @Test
    void existingGroup_advancesToMaxPlusOne() {
        List<GroupMax> existing = List.of(
                new GroupMax(List.of("inc_is_sinp_count_l3m_mtd", "1", "INC"), 4));

        Map<String, Integer> resolved = resolver.resolveNextVersions(
                existing, Set.of(List.of("inc_is_sinp_count_l3m_mtd", "1", "INC")));

        assertThat(resolved).containsEntry(
                ConfigVersionResolver.groupKey("inc_is_sinp_count_l3m_mtd", "1", "INC"), 5);
    }

    @Test
    void eachGroupIsVersionedIndependently() {
        List<GroupMax> existing = List.of(
                new GroupMax(List.of("inc_is_sinp_count_l3m_mtd", "1", "INC"), 2));

        Map<String, Integer> resolved = resolver.resolveNextVersions(existing,
                Set.of(List.of("inc_is_sinp_count_l3m_mtd", "1", "INC"),
                        List.of("inc_is_mi_count_l3m_mtd", "2", "INC")));

        assertThat(resolved)
                .containsEntry(ConfigVersionResolver.groupKey("inc_is_sinp_count_l3m_mtd", "1", "INC"), 3)
                .containsEntry(ConfigVersionResolver.groupKey("inc_is_mi_count_l3m_mtd", "2", "INC"), 1);
    }

    @Test
    void differentModulesAreDifferentGroups() {
        List<GroupMax> existing = List.of(
                new GroupMax(List.of("inc_is_sinp_count_l3m_mtd", "1", "INC"), 7));

        Map<String, Integer> resolved = resolver.resolveNextVersions(existing,
                Set.of(List.of("inc_is_sinp_count_l3m_mtd", "1", "TPRM")));

        assertThat(resolved).containsEntry(
                ConfigVersionResolver.groupKey("inc_is_sinp_count_l3m_mtd", "1", "TPRM"), 1);
    }

    @Test
    void emptyBatch_resolvesNothing() {
        assertThat(resolver.resolveNextVersions(List.of(), Set.of())).isEmpty();
    }

    @Test
    void groupKey_joinsValuesWithoutCollidingOnAdjacentValues() {
        assertThat(ConfigVersionResolver.groupKey("ab", "c"))
                .isNotEqualTo(ConfigVersionResolver.groupKey("a", "bc"));
    }
}
