package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.NetRiskRating;

import java.util.Map;

/**
 * Contract for every per-module {@code *_fact_orl} snapshot entity ({@code rcsa/inc/ina/kri_fact_orl}),
 * letting {@link com.dbs.mot.grc.service.GrcMetricsService} assemble all modules uniformly.
 */
public interface ModuleFact {

    /** Module net risk rating ({@code NET_RISK_RATING}). */
    NetRiskRating getNetRiskRtng();

    /**
     * Ordered metric values keyed by their exact GRC-metric field names (matching the source
     * columns); includes derived metrics such as the KRI proportions.
     */
    Map<String, Object> metrics();
}
