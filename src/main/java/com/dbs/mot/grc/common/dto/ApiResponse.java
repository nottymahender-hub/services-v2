package com.dbs.mot.grc.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Standard JSON envelope returned by every API endpoint.
 * Null fields are omitted from the serialized output.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
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
