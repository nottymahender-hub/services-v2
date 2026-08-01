package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One metric line inside a module's GRC block (see {@link GrcModuleBlock}).
 *
 * <p>Always serialized in full so the shape is fixed: when the module has no fact row for the
 * business date, {@code value} is {@code null} and {@code riskRatingChge} is {@code "N.A"}.
 *
 * @param name           the metric's exact field name, e.g. {@code "KRI_ACTIVE_CNT"}
 * @param value          the metric value (counts as integers, proportions as percentages), or
 *                       {@code null} when there is no snapshot value
 * @param riskRatingChge the metric's neutral change vs. the comparison month
 *                       ({@code Increased/Decreased/No change/N.A})
 */
@Schema(description = "One metric of a module GRC block: its name, value and neutral change.")
public record GrcMetric(
        @Schema(description = "Metric field name", example = "KRI_ACTIVE_CNT")
        String name,

        @Schema(description = "Metric value (null when no snapshot row exists)", example = "10")
        Object value,

        @Schema(description = "Neutral metric change vs. the comparison month",
                example = "Increased", allowableValues = {"Increased", "Decreased", "No change", "N.A"})
        String riskRatingChge) {
}
