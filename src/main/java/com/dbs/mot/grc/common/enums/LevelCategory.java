package com.dbs.mot.grc.common.enums;

/**
 * Business-unit level / dimension category — shared by {@code train_stats.lvl},
 * {@code orl_lndscp_assmt_details.category} and {@code fact_orl.category}.
 */
public enum LevelCategory implements PersistableEnum {

    L2("L2"),
    L3("L3"),
    L4("L4"),
    GRP_L2("grp_l2"),
    GRP_L3("grp_l3"),
    GRP_L4("grp_l4"),
    LOC("loc");

    private final String dbValue;

    LevelCategory(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    public static LevelCategory fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(LevelCategory.class, dbValue);
    }
}
