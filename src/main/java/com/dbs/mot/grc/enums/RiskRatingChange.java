package com.dbs.mot.grc.enums;

/**
 * Direction of risk-rating change vs. the prior period — {@code fact_orl.RISK_RTNG_CHGE}.
 */
public enum RiskRatingChange implements PersistableEnum {

    IMPROVED("Improved"),
    DETERIORATED("Deteriorated"),
    STABLE("Stable"),
    NA("N.A");

    private final String dbValue;

    RiskRatingChange(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    public static RiskRatingChange fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(RiskRatingChange.class, dbValue);
    }
}
