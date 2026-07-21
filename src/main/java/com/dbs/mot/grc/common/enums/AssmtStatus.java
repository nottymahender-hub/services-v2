package com.dbs.mot.grc.common.enums;

/**
 * Landscape assessment status — {@code orl_lndscp_assmt.status}.
 */
public enum AssmtStatus implements PersistableEnum {

    DRAFT("Draft"),
    OPEN("Open"),
    LOCKED("Locked"),
    PARTIAL_LOCKED("Partial locked"),
    CLOSED("Closed");

    private final String dbValue;

    AssmtStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    public static AssmtStatus fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(AssmtStatus.class, dbValue);
    }
}
