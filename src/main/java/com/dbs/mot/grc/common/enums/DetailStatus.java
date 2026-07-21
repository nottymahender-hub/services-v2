package com.dbs.mot.grc.common.enums;

/**
 * Assessment detail-row status — {@code orl_lndscp_assmt_details.STATUS}.
 */
public enum DetailStatus implements PersistableEnum {

    OPEN("Open"),
    LOCKED("Locked"),
    PENDING_UNLOCK("Pending unlock"),
    COMPLETED("Completed");

    private final String dbValue;

    DetailStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    public static DetailStatus fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(DetailStatus.class, dbValue);
    }
}
