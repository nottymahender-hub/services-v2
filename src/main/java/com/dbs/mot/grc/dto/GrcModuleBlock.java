package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * One module's GRC block in the assessment-detail drill-down (module keys {@code RCSA/INC/INA/KRI}).
 *
 * <p><strong>The block is always present and fully populated</strong> — every module lists all of
 * its metrics in canonical order, even when no fact row exists for the business date. In that case
 * {@code nrr} is {@code "N.A"}, {@code riskRatingChge} is {@code "N.A"}, and each metric carries a
 * {@code null} value with a {@code "N.A"} change, so the response shape never varies.
 *
 * <p>{@code nrr} is the module net risk rating in its stored DB form (e.g. {@code "Med Low"}).
 * {@code riskRatingChge} is the module-level change: read from the assessment detail's
 * {@code MODULE_RISK_RTNG_CHGE} JSON for the current/previous blocks, computed on the fly for live.
 *
 * @param nrr            module net risk rating (DB value), or {@code "N.A"} when no fact row exists
 * @param riskRatingChge module-level risk-rating change ({@code Improved/Deteriorated/Stable/N.A})
 * @param metrics        every metric of the module, in canonical order
 */
@Schema(description = "A module's GRC block: net risk rating, module-level change and all metrics.")
public record GrcModuleBlock(
        @Schema(description = "Module net risk rating (DB value)", example = "Med Low")
        String nrr,

        @Schema(description = "Module-level risk-rating change", example = "Improved",
                allowableValues = {"Improved", "Deteriorated", "Stable", "N.A"})
        String riskRatingChge,

        @Schema(description = "All metrics of the module, in canonical order")
        List<GrcMetric> metrics) {
}
