package com.dbs.mot.grc.exception;

import com.dbs.mot.grc.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.stream.Collectors;

/**
 * Central exception handler — maps known exceptions to HTTP responses.
 * Unknown (unhandled) exceptions propagate to Spring's default 500 handler.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * HTTP 404 — a requested resource (assessment, callout, etc.) was not found.
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ex.getMessage()));
    }

    /**
     * HTTP 404 — no controller mapping (and no static resource) matches the requested path,
     * e.g. a URL that was removed or never existed. Spring Boot 3.2+ raises this as a normal
     * exception rather than short-circuiting straight to a 404 response, so without this
     * handler it would otherwise fall through to {@link #handleUnexpected} and return 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No handler for request: {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("No resource found for '" + ex.getResourcePath() + "'."));
    }

    /**
     * HTTP 405 — the path exists but does not support the requested HTTP method
     * (e.g. {@code POST} to a GET-only endpoint). Without this handler the exception
     * would fall through to {@link #handleUnexpected} and return 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure(ex.getMessage() + "."));
    }

    /**
     * HTTP 400 — {@code @Valid} on a request body failed Hibernate validation.
     * Collects all field-level constraint messages into a single readable string.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        String combined = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Request body validation failed: {}", combined);
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(combined));
    }

    /**
     * HTTP 400 — generic client-side errors (wrong file type, bad headers, etc.)
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.failure(ex.getMessage()));
    }

    /**
     * HTTP 400 — CSV content/domain validation failures.
     * Returns structured per-row error details so the user knows exactly what to fix.
     */
    @ExceptionHandler(CsvValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleCsvValidation(CsvValidationException ex) {
        log.warn("CSV validation failed: {} error(s)", ex.getErrors().size());
        return ResponseEntity.badRequest()
                .body(ApiResponse.validationFailure(
                        "CSV validation failed. Please correct the error(s) and re-upload.",
                        ex.getErrors()));
    }

    /**
     * HTTP 409 — the request conflicts with existing state (e.g. an assessment already
     * exists for the requested landscape + period).
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(ex.getMessage()));
    }

    /**
     * HTTP 409 — assessment generation found no {@code fact_orl} data for the reported period. In
     * practice always caught by {@code BulkAssmtGenerationService} and reported as a per-landscape
     * skip; this handler exists only as a defensive fallback.
     */
    @ExceptionHandler(NoFactDataException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoFactData(NoFactDataException ex) {
        log.warn("No fact data for generation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(ex.getMessage()));
    }

    /**
     * HTTP 401 — the {@code X-EGRC-UserId} header is missing from the request.
     * Spring throws {@link MissingRequestHeaderException} for any required
     * header annotated with {@code @RequestHeader} that is absent.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Missing required request header: {}", ex.getHeaderName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(
                        "Required header '" + ex.getHeaderName() + "' is missing."));
    }

    /**
     * HTTP 401 — the {@code X-EGRC-UserId} header is present but blank. Thrown by the
     * controllers' {@code requireUser(...)} guard.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(ex.getMessage()));
    }

    /**
     * HTTP 413 — uploaded file exceeds the configured maximum size.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure("Uploaded file exceeds the maximum allowed size."));
    }

    /**
     * HTTP 400 — a required query/form parameter is absent (e.g. the mandatory {@code bizDt}).
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing required request parameter: {}", ex.getParameterName());
        return ResponseEntity.badRequest().body(ApiResponse.failure(
                "Required parameter '" + ex.getParameterName() + "' is missing."));
    }

    /**
     * HTTP 400 — a path or query parameter cannot be converted to the expected type.
     * Example: {@code /landscape/abc/assessments} where {@code abc} is not a valid {@code Long}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        String expectedType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName() : "valid type";
        String message = String.format(
                "Invalid value '%s' for parameter '%s'. Expected: %s.",
                ex.getValue(), ex.getName(), expectedType);
        log.warn("Type mismatch for parameter '{}': got '{}', expected {}",
                ex.getName(), ex.getValue(), expectedType);
        return ResponseEntity.badRequest().body(ApiResponse.failure(message));
    }

    /**
     * HTTP 409 — a row violates a unique/FK constraint that was not caught by pre-validation
     * (e.g. concurrent uploads or re-upload without version bump).
     * Returns the database constraint name so the caller knows which rule was violated.
     */
    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            SQLIntegrityConstraintViolationException ex) {
        log.error("DB integrity constraint violated: {}", ex.getMessage());
        String detail = ex.getMessage() != null ? ex.getMessage() : "Unknown constraint violation.";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure(
                        "Database integrity constraint violation: " + detail));
    }

    /**
     * HTTP 500 — catch-all for any unhandled exception.
     * Logs the full stack trace server-side; returns a generic message to the caller
     * so internal details are not leaked.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("An unexpected error occurred. Please try again later."));
    }
}
