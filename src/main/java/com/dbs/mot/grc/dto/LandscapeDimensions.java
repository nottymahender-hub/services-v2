package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Landscape-config metadata for {@code GET /landscape/{lndscpAssmtId}/dimensions}.
 *
 * <pre>
 * {
 *   "riskAreas": { "IT Resiliency and Continuity": ["OR","LCS"], "IT Change and Release Management": ["LCS"] },
 *   "riskClusters": ["OR","LCS"],
 *   "buDetails": { "lvl": 2, "bizUnits": ["Technology","Operations"] },
 *   "locations": ["SG", "CN"]
 * }
 * </pre>
 *
 * <p>Null properties are serialized (no {@code NON_NULL} filtering).
 */
@Getter
@Builder
public class LandscapeDimensions {

    /** Risk area name → its risk cluster codes, from {@code orl_lndscp_dim.RISK_AREA}. */
    private final Map<String, List<String>> riskAreas;

    /** Distinct risk cluster codes across all risk areas. */
    private final List<String> riskClusters;

    /** BU hierarchy level together with the BU name list. */
    private final LandscapeBuDetails buDetails;

    /** Locations from {@code orl_lndscp_dim.LOCATIONS} as a JSON array. */
    private final List<String> locations;
}
