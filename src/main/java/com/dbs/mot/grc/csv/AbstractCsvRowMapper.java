package com.dbs.mot.grc.csv;

import com.dbs.mot.grc.dto.ValidationErrorDetail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for all CSV row mappers.
 *
 * <p>Contains the reusable parse helpers so concrete mappers only need to
 * implement {@link #map(Map, int, List)} — no duplicated boilerplate.
 *
 * <h3>Conventions</h3>
 * <ul>
 *   <li>Every {@code parseRequired*} method adds a {@link ValidationErrorDetail}
 *       and returns {@code null} on failure.</li>
 *   <li>A mapper should return {@code null} from {@code map()} if any required
 *       field is {@code null}, signalling to the processor that this row has
 *       unrecoverable errors and must be excluded from cross-row validation.</li>
 * </ul>
 *
 * @param <T> the DTO type produced for a single CSV row
 */
public abstract class AbstractCsvRowMapper<T> implements CsvRowMapper<T> {

    // ── Required integer ─────────────────────────────────────────────────────

    protected Integer parseRequiredInt(Map<String, String> row, String field,
                                       int rowNum, List<ValidationErrorDetail> errors) {
        String raw = row.getOrDefault(field, "").trim();
        if (raw.isEmpty()) {
            errors.add(error(rowNum, field, field + " is required and must not be empty."));
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            errors.add(error(rowNum, field,
                    field + " must be a valid integer, but got: '" + raw + "'"));
            return null;
        }
    }

    // ── Required string ──────────────────────────────────────────────────────

    protected String parseRequiredString(Map<String, String> row, String field,
                                         int rowNum, List<ValidationErrorDetail> errors) {
        String value = row.getOrDefault(field, "").trim();
        if (value.isEmpty()) {
            errors.add(error(rowNum, field, field + " is required and must not be empty."));
            return null;
        }
        return value;
    }

    // ── Optional string (null when blank) ────────────────────────────────────

    protected String parseOptionalString(Map<String, String> row, String field) {
        String value = row.getOrDefault(field, "").trim();
        return value.isEmpty() ? null : value;
    }

    // ── Required decimal ─────────────────────────────────────────────────────

    protected BigDecimal parseRequiredDecimal(Map<String, String> row, String field,
                                              int rowNum, List<ValidationErrorDetail> errors) {
        String raw = row.getOrDefault(field, "").trim();
        if (raw.isEmpty()) {
            errors.add(error(rowNum, field, field + " is required and must not be empty."));
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            errors.add(error(rowNum, field,
                    field + " must be a valid decimal number, but got: '" + raw + "'"));
            return null;
        }
    }

    // ── Optional decimal (null when blank, no error) ─────────────────────────

    /**
     * Parses an optional decimal cell. Returns {@code null} when the cell is blank
     * (allowing the caller to substitute a sentinel "open-ended" bound), or reports
     * a validation error when the cell is non-blank but not a valid number.
     */
    protected BigDecimal parseOptionalDecimal(Map<String, String> row, String field,
                                              int rowNum, List<ValidationErrorDetail> errors) {
        String raw = row.getOrDefault(field, "").trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            errors.add(error(rowNum, field,
                    field + " must be a valid decimal number, but got: '" + raw + "'"));
            return null;
        }
    }

    // ── Required double ──────────────────────────────────────────────────────

    protected Double parseRequiredDouble(Map<String, String> row, String field,
                                         int rowNum, List<ValidationErrorDetail> errors) {
        String raw = row.getOrDefault(field, "").trim();
        if (raw.isEmpty()) {
            errors.add(error(rowNum, field, field + " is required and must not be empty."));
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            errors.add(error(rowNum, field,
                    field + " must be a valid number, but got: '" + raw + "'"));
            return null;
        }
    }

    // ── Required enum ────────────────────────────────────────────────────────

    /**
     * Parses a required field and validates it against a whitelist of allowed values.
     * Returns {@code null} (and records an error) when the value is absent or not in
     * {@code validValues}.
     */
    protected String parseRequiredEnum(Map<String, String> row, String field,
                                       int rowNum, List<ValidationErrorDetail> errors,
                                       Set<String> validValues) {
        String value = row.getOrDefault(field, "").trim();
        if (value.isEmpty()) {
            errors.add(error(rowNum, field, field + " is required and must not be empty."));
            return null;
        }
        if (!validValues.contains(value)) {
            errors.add(error(rowNum, field,
                    field + " must be one of " + validValues + ", but got: '" + value + "'"));
            return null;
        }
        return value;
    }

    // ── Required LocalDate (ISO format yyyy-MM-dd) ───────────────────────────

    protected LocalDate parseRequiredLocalDate(Map<String, String> row, String field,
                                               int rowNum, List<ValidationErrorDetail> errors) {
        String raw = row.getOrDefault(field, "").trim();
        if (raw.isEmpty()) {
            errors.add(error(rowNum, field, field + " is required and must not be empty."));
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            errors.add(error(rowNum, field,
                    field + " must be a valid date in yyyy-MM-dd format, but got: '" + raw + "'"));
            return null;
        }
    }

    // ── Convenience factory ──────────────────────────────────────────────────

    protected ValidationErrorDetail error(int row, String field, String message) {
        return ValidationErrorDetail.builder().row(row).field(field).message(message).build();
    }
}
