package com.dbs.mot.grc.dto;

/**
 * Projection of the current maximum {@code config_version} per natural-key group in
 * {@code net_risk_band}. Fed to {@link com.dbs.mot.grc.util.ConfigVersionResolver}.
 *
 * @param netRiskRtng the net risk rating
 * @param module      the module
 * @param maxVersion  the highest {@code config_version} currently stored for this group
 */
public record NetRiskBandVersionGroup(
        String netRiskRtng,
        String module,
        Integer maxVersion) {
}
