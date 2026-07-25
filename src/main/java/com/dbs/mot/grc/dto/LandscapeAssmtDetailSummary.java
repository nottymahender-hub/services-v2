package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
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
 *   "lndscpLastRefreshed": "2026-07-03",
 *   "lndscpLastModifiedOn": "2026-07-03T12:18:40.802",
 *   "lndscpLastModifiedBy": "editor",
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
 * <p>Every field is always serialized, including nulls, so the response shape is stable
 * regardless of which optional values are present.
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

    /**
     * The latest {@code fact_orl.biz_dt} across the snapshot rows matching this assessment's
     * detail dimension keys — i.e. when the underlying computed data was last refreshed.
     * {@code null} when no matching fact rows exist.
     */
    private final LocalDate lndscpLastRefreshed;

    /** {@code orl_lndscp_assmt.UPDATE_DT_TM} when present, else {@code CREATE_DT_TM}. */
    private final LocalDateTime lndscpLastModifiedOn;

    /** {@code orl_lndscp_assmt.UPDATED_BY} when present, else {@code CREATED_BY}. */
    private final String lndscpLastModifiedBy;

    /** Per-row detail items from {@code orl_lndscp_assmt_details}. */
    private final List<LandscapeAssmtDetailItem> assessments;
}
