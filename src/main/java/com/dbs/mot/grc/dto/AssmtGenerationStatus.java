package com.dbs.mot.grc.dto;

/**
 * Per-landscape outcome of a bulk assessment-generation run
 * ({@code POST /landscape/assessments/generate}).
 */
public enum AssmtGenerationStatus {

    /** The assessment (and its detail rows) was generated successfully. */
    GENERATED,

    /** Skipped — an assessment already exists for this landscape and the current period. */
    SKIPPED_ALREADY_EXISTS,

    /**
     * Skipped — more than one ACTIVE config row is effective today for this landscape
     * name, so the config to generate from cannot be determined unambiguously.
     */
    SKIPPED_AMBIGUOUS_CONFIG
}
