package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Top-level response object for {@code GET /landscape/{lndscpAssmtId}/assessments}.
 *
 * <pre>
 * {
 *   "lndscpName": "DBS GROUP SG-CN",
 *   "lndscpAssmtId": 123,
 *   "lndscpAssmtPeriod": "June 2026",
 *   "lndscpAssmtStatus": "Draft",
 *   "lndscpLastRefreshed": "2026-07-03T12:18:40.802",
 *   "assessments": [ { "id": 1, "riskArea": "Market Abuse", ... } ]
 * }
 * </pre>
 *
 * <p>Landscape-config metadata (dimensions) is served separately by
 * {@code GET /landscape/{lndscpAssmtId}/dimensions}.
 *
 * <p>All fields are populated from {@code orl_lndscp_assmt} joined with its parent
 * {@code orl_lndscp_dim}; when {@code lndscpAssmtId} does not exist, the service
 * throws {@link com.dbs.mot.grc.common.exception.NotFoundException} (HTTP 404)
 * rather than returning a partially-empty object.
 *
 * <p>Null properties are serialized (no {@code NON_NULL} filtering) so the response
 * shape is stable regardless of which optional values are present.
 */
@Getter
@Builder
public class LandscapeAssmtDetailSummary {

    /** {@code orl_lndscp_dim.LNDSCP_NM}. */
    private final String lndscpName;

    /** {@code orl_lndscp_assmt.id}. */
    private final Long lndscpAssmtId;

    /** {@code orl_lndscp_assmt.ASSEMT_PERIOD}. */
    private final String lndscpAssmtPeriod;

    /** {@code orl_lndscp_assmt.status}. */
    private final String lndscpAssmtStatus;

    /** {@code UPDATE_DT_TM} when present, else {@code CREATE_DT_TM}. */
    private final LocalDateTime lndscpLastRefreshed;

    /** Per-row detail items from {@code orl_lndscp_assmt_details}. */
    private final List<LandscapeAssmtDetailItem> assessments;
}
