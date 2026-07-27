package com.dbs.mot.grc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/**
 * CSV row DTO for {@code orl_risk_type_risk_area_map}.
 * Upload headers: RISK_AREA, RISK_TYPE_L4_NUM, RISK_TYPE_L4_NM, IS_OR_FA, RISK_CLUSTER
 */
@Getter
@Builder
public class RiskTypeRiskAreaMapCsvRow {

    @NotBlank(message = "RISK_AREA is required.")
    @Size(max = 120, message = "RISK_AREA must not exceed 120 characters.")
    @Schema(description = "Risk area code.", example = "Market Abuse")
    private final String riskArea;

    @NotNull(message = "RISK_TYPE_L4_NUM is required and must be a valid integer.")
    @Schema(description = "Level-4 risk type number.", example = "89")
    private final Integer riskTypeL4Num;

    @NotBlank(message = "RISK_TYPE_L4_NM is required.")
    @Size(max = 120, message = "RISK_TYPE_L4_NM must not exceed 120 characters.")
    @Schema(description = "Level-4 risk type name.", example = "Market Abuse")
    private final String riskTypeL4Nm;

    /**
     * Operational-risk / financial-advisory flag. The mapper only ever supplies {@code "Y"} or
     * {@code "N"} (anything else is rejected during mapping); the {@code @Pattern} is a defensive
     * second layer for the Bean Validation phase.
     */
    @NotBlank(message = "IS_OR_FA is required.")
    @Pattern(regexp = "[YN]", message = "IS_OR_FA must be 'Y' or 'N'.")
    @Schema(description = "Operational-risk / financial-advisory flag.",
            allowableValues = {"Y", "N"}, example = "Y")
    private final String isOrFa;

    /** Optional risk-cluster code; unique per {@code (RISK_AREA, RISK_TYPE_L4_NUM)} within a batch. */
    @Size(max = 50, message = "RISK_CLUSTER must not exceed 50 characters.")
    @Schema(description = "Optional risk-cluster code (unique per RISK_AREA + RISK_TYPE_L4_NUM).",
            example = "OR", nullable = true)
    private final String riskCluster;
}
