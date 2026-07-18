package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * A single row from {@code orl_lndscp_assmt_details}, nested inside
 * {@link LandscapeAssmtDetailSummary#getAssessments()}.
 *
 * <p>Contains only per-row fields. Landscape-config metadata is hoisted to the
 * top-level {@link LandscapeAssmtDetailSummary} wrapper under {@code dimensions}.
 *
 * <p>Null properties are serialized (no {@code NON_NULL} filtering) so every field
 * is always present in each assessment item.
 */
@Getter
@Builder
public class LandscapeAssmtDetailItem {

    private final Long id;

    /** Risk area code from {@code orl_lndscp_assmt_details.RISK_AREA} (e.g. "OR", "CR"). */
    private final String riskArea;

    /**
     * BU name resolved from the raw hierarchy columns based on
     * {@code orl_lndscp_dim.BIZ_UNIT_LVL}: level 2 → {@code ORL_BU_NM_L2},
     * level 3 → {@code ORL_BU_NM_L3}, level 4 → {@code ORL_BU_NM_L4}.
     */
    private final String bu;

    /**
     * {@code LOCATION} when {@code category} is one of {@code L2, L3, L4, loc};
     * the literal {@code "Group"} when {@code category} is one of
     * {@code grp_l2, grp_l3, grp_l4}.
     */
    private final String location;

    private final String status;

    /** Calculated net risk rating — {@code fact_orl.CAL_NET_RISK_RTNG} (current biz date). */
    private final String nrrCalculated;

    /** Overlaid (analyst-adjusted) net risk rating — {@code OVRLY_NET_RISK_RTNG}. */
    private final String nrr;

    /** {@code fact_orl.RISK_RTNG_CHGE} (current biz date). */
    private final String riskRatingChange;

    /** {@code fact_orl.COMMENTARY} (current biz date). */
    private final String summary;

    private final String category;

    /**
     * Previous assessment's final rating for this dimension: the prior detail row's
     * {@code OVRLY_NET_RISK_RTNG}, falling back to the prior month's
     * {@code fact_orl.CAL_NET_RISK_RTNG}.
     */
    private final String prevAssmtFinalNRR;

    /** {@code "Y"} when {@code OVRLY_NET_RISK_RTNG} is present, else {@code "N"}. */
    private final String nrrOverlaid;
}
