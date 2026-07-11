package com.dbs.mot.grc.common.exception;

/**
 * Thrown for client-side errors that should result in HTTP 400 responses.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
