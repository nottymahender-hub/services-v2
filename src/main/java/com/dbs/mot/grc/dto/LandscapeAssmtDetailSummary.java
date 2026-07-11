package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 *   "dimensions": {
 *     "riskAreas": { "Market Abuse": ["OR", "LCS"] },
 *     "buDetails": { "lvl": 2, "bizUnits": ["CBG", "IBG"] },
 *     "locations": ["SG", "CN"]
 *   },
 *   "assessments": [ { "id": 1, "riskArea": "Market Abuse", ... } ]
 * }
 * </pre>
 *
 * <p>All fields are populated from {@code orl_lndscp_assmt} joined with its parent
 * {@code orl_lndscp_dim}; when {@code lndscpAssmtId} does not exist, the service
 * throws {@link com.dbs.mot.grc.common.exception.NotFoundException} (HTTP 404)
 * rather than returning a partially-empty object.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    /** Landscape-config metadata from {@code orl_lndscp_dim}, common to the whole assessment. */
    private final LandscapeDimensions dimensions;

    /** Per-row detail items from {@code orl_lndscp_assmt_details}. */
    private final List<LandscapeAssmtDetailItem> assessments;
}
