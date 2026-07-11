package com.dbs.mot.grc.dto;

/**
 * The shared dimension identity of an assessment detail row / fact row, used as an
 * in-memory map key to match generated rows against module fact rows and against the
 * previous assessment's rows.
 *
 * <p>As a record it gets null-safe {@code equals}/{@code hashCode} for free — important
 * because {@code ORL_BU_NM_*} and {@code LOCATION} are legitimately null for some
 * categories. {@code biz_dt} is intentionally NOT part of the key: fact rows are already
 * fetched filtered to the target date, so it never varies within a match set.
 */
public record DimensionKey(
        String riskArea,
        String orlBuNmL2,
        String orlBuNmL3,
        String orlBuNmL4,
        String location,
        String category
) {}
