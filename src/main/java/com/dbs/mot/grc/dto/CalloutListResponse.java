package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Response DTO for GET {@code /landscape/{lndscp_assmt_id}/callouts}.
 *
 * <p>Bundles the valid dimension options (for UI dropdowns) with the list of
 * active callouts belonging to the given assessment.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalloutListResponse {

    /** Valid option sets for RISK_AREA, LOCATIONS, and BIZ_UNITS fields. */
    private final CalloutDimensions dimensions;

    /** Active (not soft-deleted) callouts for the assessment. */
    private final List<CalloutResponse> callouts;
}
