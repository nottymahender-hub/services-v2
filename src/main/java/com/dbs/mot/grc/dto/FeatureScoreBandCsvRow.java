package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Validated row from the {@code feature_score_band} CSV upload.
 */
@Getter
@Builder
public class FeatureScoreBandCsvRow {

    @NotNull(message = "feature_bin is required and must be a valid integer.")
    private final Integer featureBin;

    @NotBlank(message = "feature_name is required and must not be empty.")
    @Size(max = 64, message = "feature_name must not exceed 64 characters.")
    private final String featureName;

    /** Nullable — blank cell means "open-ended low bound"; the mapper fills the sentinel. */
    private final BigDecimal rangeLow;

    /** Nullable — blank cell means "open-ended high bound"; the mapper fills the sentinel. */
    private final BigDecimal rangeHigh;

    @NotNull(message = "score is required and must be a valid integer.")
    private final Integer score;

    @NotBlank(message = "module is required.")
    private final String module;
}
