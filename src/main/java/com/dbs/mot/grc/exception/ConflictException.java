package com.dbs.mot.grc.exception;

/**
 * Thrown when a request conflicts with existing state (e.g. generating an assessment for
 * a landscape + period that already has one). Handled by {@link GlobalExceptionHandler}
 * → HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
