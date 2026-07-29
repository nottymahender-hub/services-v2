package com.dbs.mot.grc.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link SgtDateTimes} — UTC → Singapore (+8h) conversion. */
class SgtDateTimesTest {

    @Test
    void toSgt_shiftsUtcByEightHours() {
        LocalDateTime utc = LocalDateTime.parse("2026-07-05T09:00:00");
        assertThat(SgtDateTimes.toSgt(utc)).isEqualTo(LocalDateTime.parse("2026-07-05T17:00:00"));
    }

    @Test
    void toSgt_rollsOverMidnight() {
        // 20:00 UTC + 8h → 04:00 the next day in SGT.
        LocalDateTime utc = LocalDateTime.parse("2026-07-05T20:00:00");
        assertThat(SgtDateTimes.toSgt(utc)).isEqualTo(LocalDateTime.parse("2026-07-06T04:00:00"));
    }

    @Test
    void toSgt_nullReturnsNull() {
        assertThat(SgtDateTimes.toSgt(null)).isNull();
    }
}
