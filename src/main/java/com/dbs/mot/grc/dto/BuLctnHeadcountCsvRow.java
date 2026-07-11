package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/**
 * CSV row DTO for {@code orl_bu_loctn_headcount}.
 * Upload headers: ORL_BU_NM_L2, ORL_BU_NM_L3, ORL_BU_NM_L4, location, headcount
 */
@Getter
@Builder
public class BuLctnHeadcountCsvRow {

    @NotBlank(message = "ORL_BU_NM_L2 is required.")
    @Size(max = 120, message = "ORL_BU_NM_L2 must not exceed 120 characters.")
    private final String orlBuNmL2;

    @Size(max = 120, message = "ORL_BU_NM_L3 must not exceed 120 characters.")
    private final String orlBuNmL3;

    @Size(max = 120, message = "ORL_BU_NM_L4 must not exceed 120 characters.")
    private final String orlBuNmL4;

    @NotBlank(message = "location is required.")
    @Size(max = 50, message = "location must not exceed 50 characters.")
    private final String location;

    @NotNull(message = "headcount is required and must be a valid integer.")
    @Min(value = 0, message = "headcount must be >= 0.")
    private final Integer headcount;
}
