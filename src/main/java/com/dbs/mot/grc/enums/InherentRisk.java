package com.dbs.mot.grc.enums;

/**
 * Inherent risk level — {@code fact_orl.INHERENT_RISK}.
 */
public enum InherentRisk implements PersistableEnum {

    MINOR("Minor"),
    MODERATE("Moderate"),
    MAJOR("Major");

    private final String dbValue;

    InherentRisk(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    public static InherentRisk fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(InherentRisk.class, dbValue);
    }
}
