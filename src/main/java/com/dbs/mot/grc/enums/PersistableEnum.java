package com.dbs.mot.grc.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Contract for enums that map to a database column whose stored value is not necessarily a
 * valid Java identifier (e.g. {@code "Med Low"}, {@code "Poor/Fail"}).
 *
 * <p>Persistence uses {@link #getDbValue()} via the registered Spring Data JDBC converters
 * (see {@code com.dbs.mot.grc.config.JdbcConfig}); {@code @JsonValue} makes the same
 * value the default JSON representation if such an enum is ever serialized directly.
 */
public interface PersistableEnum {

    /** The exact value stored in / read from the database column. */
    @JsonValue
    String getDbValue();

    /** Null-safe accessor for building responses: returns {@code null} for a {@code null} enum. */
    static String dbValue(PersistableEnum value) {
        return value == null ? null : value.getDbValue();
    }
}
