package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Landscape-config metadata fetched from {@code orl_lndscp_dim}, returned
 * as the {@code dimensions} field in {@link LandscapeAssmtDetailSummary}.
 *
 * <pre>
 * "dimensions": {
 *   "riskAreas": {
 *     "Market Abuse":   ["OR", "LCS"],
 *     "External Fraud": ["OR"]
 *   },
 *   "buDetails": { "lvl": 2, "bizUnits": ["CBG", "IBG"] },
 *   "locations": ["SG", "CN"]
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code riskAreas} — parsed from {@code orl_lndscp_dim.RISK_AREA} JSON</li>
 *   <li>{@code buDetails} — BU hierarchy level and BU name list</li>
 *   <li>{@code locations} — {@code orl_lndscp_dim.LOCATIONS} split on commas</li>
 * </ul>
 *
 * <p>Null properties are serialized (no {@code NON_NULL} filtering).
 */
@Getter
@Builder
public class LandscapeDimensions {

    /** Risk area JSON map: key = risk area name, value = list of risk type codes. */
    private final Map<String, List<String>> riskAreas;

    /** BU hierarchy level together with the BU name list. */
    private final LandscapeBuDetails buDetails;

    /** Locations from {@code orl_lndscp_dim.LOCATIONS} as a JSON array. */
    private final List<String> locations;
}
