package com.dbs.mot.grc.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the {@code config_version} to assign to each distinct grouping key present
 * in a CSV batch for the three scoring tables enumerated by {@link ConfigTable}.
 *
 * <h3>Business rule</h3>
 * Each row's {@code config_version} is computed server-side as
 * {@code MAX(existing config_version for this group) + 1} (or {@code 1} if the group has
 * never been uploaded), where "group" is the table-specific natural key
 * (e.g. {@code (feature_name, feature_bin, module)}).
 *
 * <h3>SQL safety</h3>
 * The statement executed for a table is a compile-time constant owned by its {@link ConfigTable}
 * constant. Nothing is concatenated here, and the caller cannot name a table that has no
 * constant — so there is no injection surface. (Identifiers such as table and column names
 * cannot be bound as JDBC parameters, which is why static SQL, not parameterisation, is the
 * correct control for this query.)
 *
 * <h3>Portability</h3>
 * Tuple {@code IN} (e.g. {@code (a,b,c) IN ((1,2,3),(4,5,6))}) is supported by both
 * MariaDB and H2, but to avoid relying on that dialect-sensitive feature this resolver
 * reads the grouped maxima for the whole table and matches them in memory. These lookup
 * tables are small (config data, not transactional data) so this is cheap and keeps the
 * SQL trivially portable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigVersionResolver {

    /**
     * Delimiter used to join group-by column values into a single map key. {@code U+0001}
     * (start-of-heading) is written as an explicit escape — it is a control character that
     * cannot occur in the CSV data, so joined keys can never collide.
     */
    private static final String KEY_DELIM = "\u0001";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Computes {@code MAX(config_version) + 1} (or {@code 1} when absent) for every
     * distinct grouping key found in {@code groupKeys}, in a single query.
     *
     * @param table     the scoring table being loaded
     * @param groupKeys distinct group keys present in the current batch, each key being the
     *                  ordered list of values matching {@link ConfigTable#groupByColumns()}
     * @return a map from group key (see {@link #groupKey(String...)}) to the resolved
     *         next {@code config_version}
     */
    public Map<String, Integer> resolveNextVersions(ConfigTable table, Set<List<String>> groupKeys) {
        log.debug("Resolving config_version for {} group(s) in '{}'", groupKeys.size(), table.tableName());
        if (groupKeys.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> currentMax = readGroupedMaxVersions(table);
        Map<String, Integer> resolved = new HashMap<>();
        for (List<String> key : groupKeys) {
            String joined = groupKey(key.toArray(new String[0]));
            resolved.put(joined, currentMax.getOrDefault(joined, 0) + 1);
        }

        log.info("Resolved config_version for {} group(s) in '{}' ({} group(s) already existed)",
                resolved.size(), table.tableName(), currentMax.size());
        return resolved;
    }

    /**
     * Reads the current {@code MAX(config_version)} per existing group in the table, keyed the
     * same way as the caller's batch keys so the two can be matched in memory.
     */
    private Map<String, Integer> readGroupedMaxVersions(ConfigTable table) {
        List<String> columns = table.groupByColumns();
        Map<String, Integer> currentMax = new HashMap<>();
        jdbcTemplate.query(table.maxVersionSql(), rs -> {
            String[] values = new String[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                values[i] = rs.getString(columns.get(i));
            }
            currentMax.put(groupKey(values), rs.getInt("max_version"));
        });
        return currentMax;
    }

    /** Joins ordered group-by column values into a single lookup key. */
    public static String groupKey(String... values) {
        return String.join(KEY_DELIM, values);
    }
}
