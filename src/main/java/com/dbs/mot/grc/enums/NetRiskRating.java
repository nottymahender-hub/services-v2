package com.dbs.mot.grc.enums;

/**
 * Net risk rating — {@code net_risk_band.net_risk_rtng},
 * {@code orl_lndscp_assmt_details.OVRLY_NET_RISK_RTNG} and {@code fact_orl.CAL_NET_RISK_RTNG}.
 *
 * <p>Carries two representations: {@link #getDbValue()} (compact, stored/CSV form) and
 * {@link #getDisplayValue()} (the human-friendly label returned by the read APIs).
 */
public enum NetRiskRating implements PersistableEnum {

    LOW("Low", "Low Risk"),
    MED_LOW("Med Low", "Medium-Low Risk"),
    MED_HIGH("Med High", "Medium-High Risk"),
    HIGH("High", "High Risk");

    private final String dbValue;
    private final String displayValue;

    NetRiskRating(String dbValue, String displayValue) {
        this.dbValue = dbValue;
        this.displayValue = displayValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    /** The label shown in API responses, e.g. {@code "Medium-Low Risk"}. */
    public String getDisplayValue() {
        return displayValue;
    }

    public static NetRiskRating fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(NetRiskRating.class, dbValue);
    }

    /** Null-safe display label for building responses. */
    public static String display(NetRiskRating rating) {
        return rating == null ? null : rating.displayValue;
    }
}
