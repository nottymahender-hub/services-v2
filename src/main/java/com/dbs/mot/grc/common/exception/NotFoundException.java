package com.dbs.mot.grc.common.exception;

/**
 * Thrown when a requested resource cannot be found in the database.
 * Handled by {@link GlobalExceptionHandler} → HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
