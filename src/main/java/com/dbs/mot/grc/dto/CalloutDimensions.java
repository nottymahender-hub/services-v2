package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Valid option sets derived from {@code orl_lndscp_dim} for a given assessment,
 * returned as the {@code dimensions} block in the GET-all-callouts response.
 *
 * <ul>
 *   <li>{@code validRiskAreas} â€” JSON keys from {@code RISK_AREA} + {@code "Others"}</li>
 *   <li>{@code validLocations} â€” {@code LOCATIONS} CSV values + {@code "Others"} + {@code "ALL"}</li>
 *   <li>{@code validBizUnits}  â€” {@code BIZ_UNITS}  CSV values + {@code "Others"} + {@code "ALL"}</li>
 * </ul>
 */
@Getter
@Builder
public class CalloutDimensions {

    private final List<String> validRiskAreas;
    private final List<String> validLocations;
    private final List<String> validBizUnits;
}
