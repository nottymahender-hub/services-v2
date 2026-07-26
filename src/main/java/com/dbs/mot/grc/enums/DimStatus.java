package com.dbs.mot.grc.enums;

/**
 * Landscape config status — {@code orl_lndscp_dim.STATUS}.
 */
public enum DimStatus implements PersistableEnum {

    ACTIVE("ACTIVE"),
    DEACTIVATED("DEACTIVATED");

    private final String dbValue;

    DimStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    public static DimStatus fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(DimStatus.class, dbValue);
    }
}
