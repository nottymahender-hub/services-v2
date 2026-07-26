package com.dbs.mot.grc.exception;

import com.dbs.mot.grc.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleConstraintViolation_returns409WithDetail() {
        SQLIntegrityConstraintViolationException ex =
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry 'CFG001-1' for key 'idx_lndscp_unique'");

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage())
                .contains("integrity constraint")
                .contains("Duplicate entry");
    }

    @Test
    void handleConstraintViolation_nullMessage_returnsGenericDetail() {
        SQLIntegrityConstraintViolationException ex =
                new SQLIntegrityConstraintViolationException((String) null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("Unknown constraint violation");
    }

    @Test
    void handleFileTooLarge_returns413() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10_000_000L);

        ResponseEntity<ApiResponse<Void>> response = handler.handleFileTooLarge(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("maximum allowed size");
    }

    @Test
    void handleBadRequest_returns400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadRequest(new BadRequestException("bad input"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo("bad input");
    }

    @Test
    void handleConflict_returns409() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleConflict(new ConflictException("already exists"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).isEqualTo("already exists");
    }

    @Test
    void handleMethodNotSupported_returns405NotUnexpected500() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("POST");
    }

    @Test
    void handleNoResourceFound_returns404NotUnexpected500() {
        NoResourceFoundException ex =
                new NoResourceFoundException(HttpMethod.GET, "/api/orl-configurations/uploads");

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResourceFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("/api/orl-configurations/uploads");
    }
}
