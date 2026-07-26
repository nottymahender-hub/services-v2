package com.dbs.mot.grc.exception;

/**
 * Thrown when a request lacks a valid operator identity — i.e. the {@code X-EGRC-UserId} header
 * is present but blank. Mapped to HTTP 401 by
 * {@link GlobalExceptionHandler#handleUnauthorized(UnauthorizedException)}.
 *
 * <p>A completely absent required header is raised by Spring as
 * {@code MissingRequestHeaderException} and mapped to 401 separately.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
