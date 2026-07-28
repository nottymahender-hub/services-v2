package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response for the overlay-save endpoint
 * ({@code POST /landscape/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}/overlay}).
 *
 * <p>Echoes the persisted overlay fields plus the (possibly re-evaluated) risk-rating change, with
 * the assessment and detail ids for correlation. Ratings are the stored DB values.
 */
@Getter
@Builder
public class OverlayResponse {

    /** {@code orl_lndscp_assmt.id} (from the request path). */
    private final Long lndscpAssmtId;

    /** {@code orl_lndscp_assmt_details.id} that was updated. */
    private final Long assmtDetailId;

    /** {@code OVRLY_NET_RISK_RTNG} db value, or {@code null} when the overlay was cleared. */
    private final String overlaidNRR;

    /** {@code OVRLY_JSTFKN}, or {@code null}. */
    private final String overlayJstfkn;

    /** {@code STATUS} db value of the detail row after the save. */
    private final String status;

    /**
     * {@code RISK_RTNG_CHGE} db value after the save. Re-evaluated (previous assessment's final NRR
     * vs. this detail's effective NRR) when the overlaid rating changed; otherwise unchanged.
     */
    private final String riskRatingChange;
}
