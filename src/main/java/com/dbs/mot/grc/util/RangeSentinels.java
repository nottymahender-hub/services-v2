package com.dbs.mot.grc.util;

import java.math.BigDecimal;

/**
 * Sentinel values used to represent an "open-ended" bound (blank {@code range_low} /
 * {@code range_high} cell) in the {@code feature_score_band} and {@code net_risk_band}
 * CSVs.
 *
 * <p>The business-requested sentinel magnitude is {@code 9999999999.999999999}
 * (9 fractional digits), but both target columns are {@code DECIMAL(20,6)} — 6
 * fractional digits max. The constants below are therefore rounded down to the
 * column's maximum precision; using the unrounded value would either throw on
 * insert (MariaDB, strict mode) or be silently truncated (H2), so rounding here
 * keeps the stored value identical across environments and explicit in code.
 */
public final class RangeSentinels {

    private RangeSentinels() {
    }

    public static final BigDecimal OPEN_LOW = new BigDecimal("-9999999999.999999");
    public static final BigDecimal OPEN_HIGH = new BigDecimal("9999999999.999999");
}
