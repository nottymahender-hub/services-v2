package com.dbs.mot.grc.enums;

/**
 * GRC source module — the {@code module} column of {@code feature_score_band},
 * {@code train_stats} and {@code net_risk_band}.
 */
public enum Module implements PersistableEnum {

    INC("INC"),
    INA("INA"),
    RCSA("RCSA"),
    KRI("KRI"),
    TPRM("TPRM");

    private final String dbValue;

    Module(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    public static Module fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(Module.class, dbValue);
    }
}
