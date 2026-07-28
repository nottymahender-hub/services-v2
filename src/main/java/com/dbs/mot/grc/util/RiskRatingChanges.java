package com.dbs.mot.grc.util;

import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.enums.RiskRatingChange;

/**
 * Single source of truth for deriving a {@link RiskRatingChange} from a pair of net risk ratings
 * (previous period vs. current period).
 *
 * <p><strong>Keep this the only place the rule lives</strong> — both the assessment-detail overlay
 * recompute and the "live" GRC-metric block delegate here, so the business rule can be changed in
 * one spot.
 *
 * <h3>Rule (severity order: High &gt; Medium-High &gt; Medium-Low &gt; Low)</h3>
 * <ul>
 *   <li>either rating absent ({@code null}, i.e. Grey/NA/NIL) → {@link RiskRatingChange#NA}</li>
 *   <li>current equals previous → {@link RiskRatingChange#STABLE}</li>
 *   <li>current is <em>less</em> severe than previous → {@link RiskRatingChange#IMPROVED}</li>
 *   <li>current is <em>more</em> severe than previous → {@link RiskRatingChange#DETERIORATED}</li>
 * </ul>
 *
 * <p>Severity is taken from the {@link NetRiskRating} declaration order
 * ({@code LOW < MED_LOW < MED_HIGH < HIGH}), i.e. its {@code ordinal()}.
 */
public final class RiskRatingChanges {

    private RiskRatingChanges() {
        // Utility class — no instances.
    }

    /**
     * Derives the risk-rating change from the previous-period rating to the current-period rating.
     *
     * @param previous the baseline (older) rating; {@code null} when there is none
     * @param current  the newer rating to compare against the baseline; {@code null} when there is none
     * @return the derived change; {@link RiskRatingChange#NA} when either rating is {@code null}
     */
    public static RiskRatingChange derive(NetRiskRating previous, NetRiskRating current) {
        if (previous == null || current == null) {
            return RiskRatingChange.NA;
        }
        int delta = Integer.compare(current.ordinal(), previous.ordinal());
        if (delta == 0) {
            return RiskRatingChange.STABLE;
        }
        // Lower ordinal = lower severity = an improvement; higher ordinal = deterioration.
        return delta < 0 ? RiskRatingChange.IMPROVED : RiskRatingChange.DETERIORATED;
    }
}
