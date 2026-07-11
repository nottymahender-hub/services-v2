package com.dbs.mot.grc.common.csv;

import com.dbs.mot.grc.common.dto.ValidationErrorDetail;

import java.util.List;

/**
 * Performs cross-row (dataset-level) validation after all rows are mapped.
 * Typical checks: uniqueness constraints, referential integrity within the CSV.
 *
 * @param <T> the DTO type being validated
 */
public interface CsvRowValidator<T> {

    /**
     * @param rows all successfully mapped rows
     * @return list of validation errors (empty if all rows are valid)
     */
    List<ValidationErrorDetail> validate(List<T> rows);
}
