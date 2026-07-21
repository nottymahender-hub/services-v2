package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A single risk area within a {@link RiskAreaGroup}, as stored in the
 * {@code orl_lndscp_dim.RISK_AREA} JSON document.
 *
 * <pre>
 * { "riskArea": "Data Governance", "riskClusters": ["OR", "LCS"] }
 * </pre>
 *
 * @param riskArea     the risk area name; unique across the whole RISK_AREA document
 *                     and used as the dimension key on generated assessment detail rows
 * @param riskClusters the risk cluster codes mapped to this risk area (never empty)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RiskAreaEntry(String riskArea, List<String> riskClusters) {
}
