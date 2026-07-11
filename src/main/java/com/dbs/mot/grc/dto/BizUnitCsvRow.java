package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/**
 * Validated row from the {@code orl_biz_unit} CSV upload.
 */
@Getter
@Builder
public class BizUnitCsvRow {

    @NotNull(message = "BU_NUM is required and must be a valid integer.")
    private final Integer buNum;

    @NotBlank(message = "BU_NM is required and must not be empty.")
    @Size(max = 1200, message = "BU_NM must not exceed 1200 characters.")
    private final String buNm;

    @NotNull(message = "LVL_OF_HIER is required and must be a valid integer.")
    private final Integer lvlOfHier;

    @Size(max = 1200, message = "ORL_BU_NM_L2 must not exceed 1200 characters.")
    private final String orlBuNmL2;

    @Size(max = 1200, message = "ORL_BU_NM_L3 must not exceed 1200 characters.")
    private final String orlBuNmL3;

    @Size(max = 1200, message = "ORL_BU_NM_L4 must not exceed 1200 characters.")
    private final String orlBuNmL4;

    @Size(max = 1200, message = "BU_FULL_PATH must not exceed 1200 characters.")
    private final String buFullPath;
}
