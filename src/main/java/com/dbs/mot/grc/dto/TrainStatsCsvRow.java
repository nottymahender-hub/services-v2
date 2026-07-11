package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Validated row from the {@code train_stats} CSV upload.
 */
@Getter
@Builder
public class TrainStatsCsvRow {

    @NotBlank(message = "lvl is required.")
    private final String lvl;

    @NotNull(message = "train_mean is required and must be a valid decimal.")
    private final BigDecimal trainMean;

    @NotNull(message = "train_var is required and must be a valid decimal.")
    private final BigDecimal trainVar;

    @NotBlank(message = "module is required.")
    private final String module;
}
