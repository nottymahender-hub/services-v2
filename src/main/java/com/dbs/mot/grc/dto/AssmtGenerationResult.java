package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Outcome of assessment generation for one landscape within a bulk run
 * ({@code POST /landscape/assessments/generate}).
 */
@Getter
@Builder
public class AssmtGenerationResult {

    /** Landscape name ({@code orl_lndscp_dim.LNDSCP_NM}). */
    private final String lndscpNm;

    /** Landscape id ({@code orl_lndscp_dim.id}); null when the config was ambiguous. */
    private final Long lndscpNum;

    /** What happened for this landscape. */
    private final AssmtGenerationStatus status;

    /** Human-readable explanation of the outcome. */
    private final String message;

    /** Id of the generated {@code orl_lndscp_assmt} row; only set when GENERATED. */
    private final Long lndscpAssmtId;

    /** Number of detail rows generated; only set when GENERATED. */
    private final Integer detailRowCount;
}
