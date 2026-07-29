package com.dbs.mot.grc.enums;

/**
 * Net risk rating — {@code net_risk_band.net_risk_rtng},
 * {@code orl_lndscp_assmt_details.OVRLY_NET_RISK_RTNG} and {@code fact_orl.CAL_NET_RISK_RTNG}.
 *
 * <p>The read APIs return the rating exactly as stored ({@link #getDbValue()}, e.g. {@code "Med Low"});
 * there is no separate display label.
 */
public enum NetRiskRating implements PersistableEnum {

    LOW("Low"),
    MED_LOW("Med Low"),
    MED_HIGH("Med High"),
    HIGH("High");

    private final String dbValue;

    NetRiskRating(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    public static NetRiskRating fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(NetRiskRating.class, dbValue);
    }
}
