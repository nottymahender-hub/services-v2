package com.dbs.mot.grc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Describes a single validation failure — which row, which field, and why.
 */
@Getter
@Builder
@AllArgsConstructor
public class ValidationErrorDetail {

    /**
     * 1-based CSV data row number (excluding the header row).
     */
    private final int row;

    /**
     * Column/field name where the error occurred.
     */
    private final String field;

    /**
     * Human-readable description of the problem.
     */
    private final String message;
}
