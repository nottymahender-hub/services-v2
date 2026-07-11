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
 * in a CSV batch for the three scoring tables ({@code feature_score_band},
 * {@code train_stats}, {@code net_risk_band}).
 *
 * <h3>Business rule</h3>
 * {@code config_version} is no longer supplied by the CSV — each row's version is now
 * computed server-side as {@code MAX(existing config_version for this group) + 1} (or
 * {@code 1} if the group has never been uploaded), where "group" is the table-specific
 * natural key (e.g. {@code (feature_name, feature_bin, module)}).
 *
 * <h3>Portability</h3>
 * Tuple {@code IN} (e.g. {@code (a,b,c) IN ((1,2,3),(4,5,6))}) is supported by both
 * MariaDB and H2, but to avoid relying on that dialect-sensitive feature this resolver
 * fetches all rows for the table with a plain query and groups/max's them in memory.
 * These lookup tables are small (config data, not transactional data) so this is cheap
 * and keeps the SQL trivially portable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigVersionResolver {

    private static final Set<String> ALLOWED_TABLES =
            Set.of("feature_score_band", "train_stats", "net_risk_band");

    /** Delimiter used to join group-by column values into a single map key. */
    private static final String KEY_DELIM = "";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Computes {@code MAX(config_version) + 1} (or {@code 1} when absent) for every
     * distinct grouping key found in {@code groupKeys}, in a single query per table.
     *
     * @param tableName   DB table — must be in the whitelist
     * @param groupByCols the natural-key columns identifying a "group" for this table,
     *                    e.g. {@code ["feature_name", "feature_bin", "module"]}
     * @param groupKeys   distinct group keys present in the current batch, each key being
     *                    the ordered list of values corresponding to {@code groupByCols}
     * @return a map from group key (see {@link #groupKey(String...)}) to the resolved
     *         next {@code config_version}
     */
    public Map<String, Integer> resolveNextVersions(String tableName, List<String> groupByCols,
                                                      Set<List<String>> groupKeys) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            throw new IllegalArgumentException(
                    "Table '" + tableName + "' is not permitted for config_version resolution.");
        }

        Map<String, Integer> currentMax = new HashMap<>();
        if (!groupKeys.isEmpty()) {
            String cols = String.join(", ", groupByCols);
            String sql = "SELECT " + cols + ", MAX(config_version) AS max_version FROM "
                    + tableName + " GROUP BY " + cols;
            jdbcTemplate.query(sql, rs -> {
                String[] values = new String[groupByCols.size()];
                for (int i = 0; i < groupByCols.size(); i++) {
                    values[i] = rs.getString(groupByCols.get(i));
                }
                currentMax.put(groupKey(values), rs.getInt("max_version"));
            });
        }

        Map<String, Integer> resolved = new HashMap<>();
        for (List<String> key : groupKeys) {
            String joined = groupKey(key.toArray(new String[0]));
            int nextVersion = currentMax.getOrDefault(joined, 0) + 1;
            resolved.put(joined, nextVersion);
        }
        log.info("Resolved config_version for {} group(s) in '{}'", resolved.size(), tableName);
        return resolved;
    }

    /**
     * Joins ordered group-by column values into a single lookup key.
     * Uses a control character delimiter unlikely to appear in the data.
     */
    public static String groupKey(String... values) {
        return String.join(KEY_DELIM, values);
    }
}
