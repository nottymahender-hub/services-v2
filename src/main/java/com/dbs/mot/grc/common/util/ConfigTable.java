package com.dbs.mot.grc.common.util;

import java.util.List;

/**
 * The three scoring-configuration tables whose {@code config_version} is assigned server-side
 * by {@link ConfigVersionResolver}.
 *
 * <p>Each constant owns the <strong>complete, static</strong> SQL used to read the existing
 * versions for its table, plus the ordered natural-key ("group") columns that SQL selects and
 * groups by. Table and column names are SQL <em>identifiers</em>, which JDBC cannot bind as
 * parameters — so holding the whole statement as a compile-time constant here is what makes
 * the read safe: no part of it is ever assembled from caller-supplied input.
 *
 * <p>Because the resolver only accepts this enum, an unsupported table is not representable
 * and needs no runtime whitelist check.
 */
public enum ConfigTable {

    /** {@code feature_score_band}, grouped by feature name + bin + module. */
    FEATURE_SCORE_BAND("feature_score_band",
            List.of("feature_name", "feature_bin", "module"),
            """
            SELECT feature_name, feature_bin, module, MAX(config_version) AS max_version
            FROM feature_score_band
            GROUP BY feature_name, feature_bin, module
            """),

    /** {@code train_stats}, grouped by level + module. */
    TRAIN_STATS("train_stats",
            List.of("lvl", "module"),
            """
            SELECT lvl, module, MAX(config_version) AS max_version
            FROM train_stats
            GROUP BY lvl, module
            """),

    /** {@code net_risk_band}, grouped by net risk rating + module. */
    NET_RISK_BAND("net_risk_band",
            List.of("net_risk_rtng", "module"),
            """
            SELECT net_risk_rtng, module, MAX(config_version) AS max_version
            FROM net_risk_band
            GROUP BY net_risk_rtng, module
            """);

    private final String tableName;
    private final List<String> groupByColumns;
    private final String maxVersionSql;

    ConfigTable(String tableName, List<String> groupByColumns, String maxVersionSql) {
        this.tableName = tableName;
        this.groupByColumns = groupByColumns;
        this.maxVersionSql = maxVersionSql;
    }

    /** The physical DB table name — used for logging only, never concatenated into SQL. */
    public String tableName() {
        return tableName;
    }

    /**
     * The natural-key columns identifying one group, in the order they appear in
     * {@link #maxVersionSql()}. Callers must supply their group-key values in this same order.
     */
    public List<String> groupByColumns() {
        return groupByColumns;
    }

    /** Static {@code MAX(config_version)} query for this table, grouped by its natural key. */
    public String maxVersionSql() {
        return maxVersionSql;
    }
}
