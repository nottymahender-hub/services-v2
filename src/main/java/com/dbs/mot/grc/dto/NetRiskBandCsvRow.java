package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Validated row from the {@code net_risk_band} CSV upload.
 */
@Getter
@Builder
public class NetRiskBandCsvRow {

    /** Nullable — blank cell means "open-ended low bound"; the mapper fills the sentinel. */
    private final BigDecimal rangeLow;

    /** Nullable — blank cell means "open-ended high bound"; the mapper fills the sentinel. */
    private final BigDecimal rangeHigh;

    @NotBlank(message = "net_risk_rtng is required.")
    private final String netRiskRtng;

    @NotBlank(message = "module is required.")
    private final String module;
}
