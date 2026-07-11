package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Response body for {@code POST /landscape/assessments/generate} — a per-landscape
 * summary of the bulk generation run.
 */
@Getter
@Builder
public class BulkAssmtGenerationResponse {

    /** Number of distinct landscape names that were active and effective today. */
    private final int totalLandscapes;

    /** How many assessments were generated. */
    private final int generated;

    /** How many landscapes were skipped (duplicate period or ambiguous config). */
    private final int skipped;

    /** One entry per landscape, in the order they were processed. */
    private final List<AssmtGenerationResult> results;
}
