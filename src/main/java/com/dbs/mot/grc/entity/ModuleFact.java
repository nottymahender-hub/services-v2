package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.common.enums.NetRiskRating;
import com.dbs.mot.grc.common.enums.RiskRatingChange;

import java.util.Map;

/**
 * Contract implemented by every per-module {@code *_fact_orl} snapshot entity
 * ({@code rcsa_fact_orl}, {@code inc_fact_orl}, {@code ina_fact_orl}, {@code kri_fact_orl}).
 *
 * <p>Lets the GRC-metrics assembler treat all modules uniformly: each contributes a JSON block
 * keyed by {@link #moduleKey()} containing its net risk rating, risk-rating change and the
 * module-specific metric fields returned by {@link #metrics()}.
 */
public interface ModuleFact {

    /** The module identifier used as the JSON key, e.g. {@code "RCSA"}, {@code "INC"}. */
    String moduleKey();

    /** Module net risk rating ({@code NET_RISK_RTNG}). */
    NetRiskRating getNetRiskRtng();

    /** Module risk-rating change ({@code RISK_RTNG_CHGE}). */
    RiskRatingChange getRiskRtngChge();

    /**
     * The module-specific metric values, ordered, keyed by their exact GRC-metric field names
     * (matching the source column names). Derived metrics (e.g. KRI proportions) are included here.
     */
    Map<String, Object> metrics();
}
