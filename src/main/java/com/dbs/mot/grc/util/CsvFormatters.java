package com.dbs.mot.grc.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Null-safe cell formatting shared by every {@code *ConfigImportService}'s CSV download
 * ({@code writeDataRows}): a plain value or a timestamp becomes an empty cell rather than the
 * literal string {@code "null"}.
 */
public final class CsvFormatters {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvFormatters() {
        // Utility class — no instances.
    }

    /** Renders any value as a CSV cell: {@code toString()}, or {@code ""} when {@code null}. */
    public static String cell(Object value) {
        return value == null ? "" : value.toString();
    }

    /** Renders a timestamp as {@code yyyy-MM-dd HH:mm:ss}, or {@code ""} when {@code null}. */
    public static String cell(LocalDateTime timestamp) {
        return timestamp == null ? "" : timestamp.format(TIMESTAMP_FORMAT);
    }
}
