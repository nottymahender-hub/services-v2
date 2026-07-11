package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/**
 * CSV row DTO for {@code orl_risk_type_risk_area_map}.
 * Upload headers: RISK_AREA, RISK_TYPE_L4_NUM, RISK_TYPE_L4_NM
 */
@Getter
@Builder
public class RiskTypeRiskAreaMapCsvRow {

    @NotBlank(message = "RISK_AREA is required.")
    @Size(max = 120, message = "RISK_AREA must not exceed 120 characters.")
    private final String riskArea;

    @NotNull(message = "RISK_TYPE_L4_NUM is required and must be a valid integer.")
    private final Integer riskTypeL4Num;

    @NotBlank(message = "RISK_TYPE_L4_NM is required.")
    @Size(max = 120, message = "RISK_TYPE_L4_NM must not exceed 120 characters.")
    private final String riskTypeL4Nm;
}
