package com.dbs.mot.grc.common.enums;

/**
 * Control-effectiveness rating — {@code fact_orl.CTRL_EFF_RTN}.
 */
public enum ControlEffectiveness implements PersistableEnum {

    POOR_FAIL("Poor/Fail"),
    ATTENTION_NEEDED_TO_SATISFACTORY("Attention Needed To Satisfactory"),
    GOOD("Good"),
    SATISFACTORY_TO_GOOD("Satisfactory to Good");

    private final String dbValue;

    ControlEffectiveness(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    public static ControlEffectiveness fromDbValue(String dbValue) {
        return PersistableEnums.fromDbValue(ControlEffectiveness.class, dbValue);
    }
}
