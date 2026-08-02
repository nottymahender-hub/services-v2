package com.dbs.mot.grc.exception;

/**
 * Thrown when assessment generation finds no {@code fact_orl} data for the reported period, so the
 * assessment cannot be generated. Handled by {@link GlobalExceptionHandler} → HTTP 409, though in
 * practice it is always caught by {@code BulkAssmtGenerationService} and reported as a per-landscape
 * skip rather than reaching the controller.
 */
public class NoFactDataException extends RuntimeException {

    public NoFactDataException(String message) {
        super(message);
    }
}
