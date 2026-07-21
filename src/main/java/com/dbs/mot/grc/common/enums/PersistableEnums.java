package com.dbs.mot.grc.common.enums;

/**
 * Helpers for resolving {@link PersistableEnum} constants from their stored DB value.
 */
public final class PersistableEnums {

    private PersistableEnums() {
    }

    /**
     * Resolves the enum constant of {@code type} whose {@link PersistableEnum#getDbValue()}
     * equals {@code dbValue}.
     *
     * @return the matching constant, or {@code null} when {@code dbValue} is {@code null}/blank
     * @throws IllegalArgumentException when {@code dbValue} matches no constant
     */
    public static <E extends Enum<E> & PersistableEnum> E fromDbValue(Class<E> type, String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            return null;
        }
        for (E constant : type.getEnumConstants()) {
            if (constant.getDbValue().equals(dbValue)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                "Unknown " + type.getSimpleName() + " database value: '" + dbValue + "'");
    }
}
