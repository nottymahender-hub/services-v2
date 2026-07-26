package com.dbs.mot.grc.exception;

import com.dbs.mot.grc.dto.ValidationErrorDetail;
import lombok.Getter;

import java.util.List;

/**
 * Thrown when CSV content fails domain-level validation.
 * Carries a list of per-row errors so callers can return them all at once.
 */
@Getter
public class CsvValidationException extends RuntimeException {

    private final List<ValidationErrorDetail> errors;

    public CsvValidationException(List<ValidationErrorDetail> errors) {
        super("CSV validation failed with " + errors.size() + " error(s)");
        this.errors = errors;
    }
}
