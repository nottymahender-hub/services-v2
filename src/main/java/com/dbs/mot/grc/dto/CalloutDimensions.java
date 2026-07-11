package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Valid option sets derived from {@code orl_lndscp_dim} for a given assessment,
 * returned as the {@code dimensions} block in the GET-all-callouts response.
 *
 * <ul>
 *   <li>{@code validRiskAreas} — JSON keys from {@code RISK_AREA} + {@code "Others"}</li>
 *   <li>{@code validLocations} — {@code LOCATIONS} CSV values + {@code "Others"} + {@code "ALL"}</li>
 *   <li>{@code validBizUnits}  — {@code BIZ_UNITS}  CSV values + {@code "Others"} + {@code "ALL"}</li>
 * </ul>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalloutDimensions {

    private final List<String> validRiskAreas;
    private final List<String> validLocations;
    private final List<String> validBizUnits;
}
