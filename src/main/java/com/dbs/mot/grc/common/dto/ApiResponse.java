package com.dbs.mot.grc.common.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Standard JSON envelope returned by every API endpoint.
 *
 * <p>Every field is always serialized, including nulls, so the envelope has one fixed shape:
 * a success response still carries {@code "errors": null} and an error response still carries
 * {@code "data": null}. Clients can read a field without first checking whether it is present.
 */
@Getter
@Builder
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final List<ValidationErrorDetail> errors;

    /**
     * Success response with no data payload (e.g. import operations).
     */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder().success(true).message(message).build();
    }

    /**
     * Error response with a plain message.
     * Generic so it can be used wherever the response type is parametrised.
     */
    public static <T> ApiResponse<T> failure(String message) {
        return ApiResponse.<T>builder().success(false).message(message).build();
    }

    /**
     * Success response carrying a typed data payload (e.g. query results).
     */
    public static <T> ApiResponse<T> successWithData(String message, T data) {
        return ApiResponse.<T>builder().success(true).message(message).data(data).build();
    }

    /**
     * Validation failure carrying per-row error details.
     */
    public static ApiResponse<Void> validationFailure(String message,
                                                      List<ValidationErrorDetail> errors) {
        return ApiResponse.<Void>builder().success(false).message(message).errors(errors).build();
    }
}
