package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response for the overlay-save endpoint
 * ({@code POST /landscape/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}/overlay}).
 *
 * <p>Echoes the persisted overlay fields, with the assessment and detail ids for correlation.
 * Ratings are the stored DB values. {@code riskRatingChange} is derived fresh from the just-saved
 * overlay — it is not a stored column.
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
     * The dimension's risk-rating change after the save, derived (not stored): previous assessment's
     * final NRR for this dimension vs. this detail's effective NRR (overlay if set, else calculated).
     */
    private final String riskRatingChange;
}
