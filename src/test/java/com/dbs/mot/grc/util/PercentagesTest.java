package com.dbs.mot.grc.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link Percentages}. */
class PercentagesTest {

    @Test
    void toPercentage_multipliesByHundredAndRoundsToTwoDecimals() {
        assertThat(Percentages.toPercentage(new BigDecimal("0.54333333")))
                .isEqualByComparingTo("54.33");
        assertThat(Percentages.toPercentage(new BigDecimal("0.005")))   // HALF_UP
                .isEqualByComparingTo("0.50");
        assertThat(Percentages.toPercentage(new BigDecimal("1")))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void toPercentage_null_returnsNull() {
        assertThat(Percentages.toPercentage(null)).isNull();
    }

    @Test
    void ratioToPercentage_computesRoundedPercentage() {
        assertThat(Percentages.ratioToPercentage(2, 4)).isEqualByComparingTo("50.00");
        assertThat(Percentages.ratioToPercentage(1, 3)).isEqualByComparingTo("33.33");
        assertThat(Percentages.ratioToPercentage(163, 300)).isEqualByComparingTo("54.33");
    }

    @Test
    void ratioToPercentage_nullOrZeroDenominator_returnsNull() {
        assertThat(Percentages.ratioToPercentage(null, 4)).isNull();
        assertThat(Percentages.ratioToPercentage(2, null)).isNull();
        assertThat(Percentages.ratioToPercentage(2, 0)).isNull();
    }
}
