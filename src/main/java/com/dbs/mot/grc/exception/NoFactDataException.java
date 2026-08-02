package com.dbs.mot.grc.exception;

/**
 * Thrown when assessment generation finds no {@code fact_orl} data for the reported period, so the
 * assessment cannot be generated. Purely an internal control-flow signal: {@code generateForDim}'s
 * only caller, {@code BulkAssmtGenerationService.generateOne}, always catches it and reports a
 * per-landscape {@code SKIPPED_NO_DATA} outcome — it is not registered in {@link GlobalExceptionHandler}
 * and must never reach an HTTP boundary.
 */
public class NoFactDataException extends RuntimeException {

    public NoFactDataException(String message) {
        super(message);
    }
}
