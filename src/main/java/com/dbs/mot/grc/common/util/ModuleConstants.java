package com.dbs.mot.grc.common.util;

import java.util.Set;

/**
 * Shared enumeration constants for ENUM-backed DB columns.
 * <p>
 * Used by CSV row mappers (per-row validation) across all three scoring tables:
 * {@code feature_score_band}, {@code train_stats}, and {@code net_risk_band}.
 * <p>
 * Keeping the valid-value sets here (rather than duplicated in each class)
 * means a single place needs updating when the DB ENUM definition changes.
 */
public final class ModuleConstants {

    private ModuleConstants() {
    }

    /**
     * Valid values for the {@code module} column. Mirrors DB ENUM definition.
     */
    public static final Set<String> VALID_MODULES = Set.of("INC", "INA", "RCSA", "KRI", "TPRM");

    /**
     * Valid values for the {@code lvl} column in {@code train_stats}.
     */
    public static final Set<String> VALID_LVL_VALUES =
            Set.of("L2", "L3", "L4", "grp_l2", "grp_l3", "grp_l4", "loc");

    /**
     * Valid values for the {@code net_risk_rtng} column in {@code net_risk_band}.
     */
    public static final Set<String> VALID_NET_RISK_RTNG =
            Set.of("Low", "Med Low", "Med High", "High");
}
