package com.dbs.mot.grc.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Converts persisted audit timestamps from UTC to Singapore time (SGT) for API responses.
 *
 * <p>All {@code CREATE_DT_TM}/{@code UPDATE_DT_TM}-style columns are stored in UTC (the database
 * default). Responses surface them in SGT, so every read path funnels through {@link #toSgt} — the
 * single place this offset rule lives.
 *
 * <p>The returned {@link LocalDateTime} carries no zone marker (the API contract keeps the plain
 * date-time shape); the value is the wall-clock time in {@code Asia/Singapore}.
 */
public final class SgtDateTimes {

    /** Singapore zone (UTC+8, no DST). */
    public static final ZoneId SGT = ZoneId.of("Asia/Singapore");

    private SgtDateTimes() {
        // Utility class — no instances.
    }

    /**
     * Converts a UTC timestamp to the equivalent Singapore wall-clock time.
     *
     * @param utc the stored timestamp, interpreted as UTC; may be {@code null}
     * @return the SGT-shifted {@link LocalDateTime}, or {@code null} when {@code utc} is {@code null}
     */
    public static LocalDateTime toSgt(LocalDateTime utc) {
        if (utc == null) {
            return null;
        }
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(SGT).toLocalDateTime();
    }
}
