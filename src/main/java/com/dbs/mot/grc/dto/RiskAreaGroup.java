package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A grouping of related risk areas, as stored in the {@code orl_lndscp_dim.RISK_AREA}
 * JSON document. The document is a JSON array of these groups.
 *
 * <pre>
 * {
 *   "groupName": "IT",
 *   "isGroup": true,
 *   "riskAreas": [
 *     { "riskArea": "IT Resiliency and Continuity", "riskClusters": ["OR", "LCS"] },
 *     { "riskArea": "IT Change and Release Management", "riskClusters": ["LCS"] }
 *   ]
 * }
 * </pre>
 *
 * @param groupName the display name of the group; required when {@code isGroup} is {@code true},
 *                  may be blank/empty for a standalone entry ({@code isGroup=false})
 * @param isGroup   {@code true} when the entry represents a real group of several risk
 *                  areas; {@code false} for a standalone risk area modelled as a group of one
 * @param riskAreas the risk areas belonging to this group (never empty)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiskAreaGroup(String groupName, boolean isGroup, List<RiskAreaEntry> riskAreas) {
}
