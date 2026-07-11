package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class OrlLndscpDimCsvRow {

    @NotBlank(message = "CONFIG_ID is required and must not be empty.")
    @Size(max = 100, message = "CONFIG_ID must not exceed 100 characters.")
    private final String configId;

    @NotBlank(message = "LNDSCP_NM is required and must not be empty.")
    @Size(max = 50, message = "LNDSCP_NM must not exceed 50 characters.")
    private final String lndscpNm;

    @NotNull(message = "EFFECT_START_DT is required and must be a valid date (yyyy-MM-dd).")
    private final LocalDate effectStartDt;

    @NotNull(message = "EFFECT_END_DT is required and must be a valid date (yyyy-MM-dd).")
    private final LocalDate effectEndDt;

    /** JSON string, e.g. {@code {"Cyber Risk": ["OR"], "Conduct Risk": ["CR"]}}. */
    @NotBlank(message = "RISK_AREA is required and must not be empty.")
    @Size(max = 1000, message = "RISK_AREA must not exceed 1000 characters.")
    private final String riskArea;

    @Size(max = 500, message = "BIZ_UNITS must not exceed 500 characters.")
    private final String bizUnits;

    @NotNull(message = "BIZ_UNIT_LVL is required and must be a valid integer.")
    @Min(value = 2, message = "BIZ_UNIT_LVL must be greater than 1.")
    private final Integer bizUnitLvl;

    @NotBlank(message = "LOCATIONS is required and must not be empty.")
    @Size(max = 100, message = "LOCATIONS must not exceed 100 characters.")
    private final String locations;
}
