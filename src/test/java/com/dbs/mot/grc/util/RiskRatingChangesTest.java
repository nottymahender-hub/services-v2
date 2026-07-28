package com.dbs.mot.grc.util;

import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.enums.RiskRatingChange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RiskRatingChanges} — the single-source risk-rating-change matrix.
 * Severity order: {@code LOW < MED_LOW < MED_HIGH < HIGH}.
 */
class RiskRatingChangesTest {

    @Test
    void equalRatings_areStable() {
        assertThat(RiskRatingChanges.derive(NetRiskRating.HIGH, NetRiskRating.HIGH))
                .isEqualTo(RiskRatingChange.STABLE);
        assertThat(RiskRatingChanges.derive(NetRiskRating.LOW, NetRiskRating.LOW))
                .isEqualTo(RiskRatingChange.STABLE);
    }

    @Test
    void lessSevereCurrent_isImproved() {
        // previous High → current Medium-High / Medium-Low / Low all improve.
        assertThat(RiskRatingChanges.derive(NetRiskRating.HIGH, NetRiskRating.MED_HIGH))
                .isEqualTo(RiskRatingChange.IMPROVED);
        assertThat(RiskRatingChanges.derive(NetRiskRating.MED_LOW, NetRiskRating.LOW))
                .isEqualTo(RiskRatingChange.IMPROVED);
    }

    @Test
    void moreSevereCurrent_isDeteriorated() {
        assertThat(RiskRatingChanges.derive(NetRiskRating.MED_LOW, NetRiskRating.HIGH))
                .isEqualTo(RiskRatingChange.DETERIORATED);
        assertThat(RiskRatingChanges.derive(NetRiskRating.LOW, NetRiskRating.MED_LOW))
                .isEqualTo(RiskRatingChange.DETERIORATED);
    }

    @Test
    void nullEitherSide_isNotApplicable() {
        assertThat(RiskRatingChanges.derive(null, NetRiskRating.HIGH)).isEqualTo(RiskRatingChange.NA);
        assertThat(RiskRatingChanges.derive(NetRiskRating.HIGH, null)).isEqualTo(RiskRatingChange.NA);
        assertThat(RiskRatingChanges.derive(null, null)).isEqualTo(RiskRatingChange.NA);
    }
}
