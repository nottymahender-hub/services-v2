package com.dbs.mot.grc.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Helpers for expressing proportions (fractions in the range 0..1) as percentages in API responses.
 *
 * <p>All results are rounded to {@value #SCALE} decimal places using {@link RoundingMode#HALF_UP}
 * (e.g. a proportion of {@code 0.54333333} becomes {@code 54.33}). Methods are null-safe: a
 * {@code null} input (or a zero denominator) yields {@code null} so "no data" stays distinct from
 * "zero percent".
 */
public final class Percentages {

    /** Decimal places retained on the percentage result. */
    public static final int SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Percentages() {
        // Utility class — no instances.
    }

    /**
     * Converts an already-computed proportion (0..1) to a percentage rounded to {@link #SCALE}
     * decimals. Returns {@code null} when {@code fraction} is {@code null}.
     */
    public static BigDecimal toPercentage(BigDecimal fraction) {
        if (fraction == null) {
            return null;
        }
        return fraction.multiply(HUNDRED).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Computes {@code numerator / denominator} as a percentage rounded to {@link #SCALE} decimals.
     * Returns {@code null} when either argument is {@code null} or the denominator is zero.
     */
    public static BigDecimal ratioToPercentage(Integer numerator, Integer denominator) {
        if (numerator == null || denominator == null || denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }
}
