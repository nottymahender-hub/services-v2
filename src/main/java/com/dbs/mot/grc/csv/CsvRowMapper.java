package com.dbs.mot.grc.csv;

import com.dbs.mot.grc.dto.ValidationErrorDetail;

import java.util.List;
import java.util.Map;

/**
 * Maps one parsed CSV row (header-to-value map) into a domain DTO.
 * Implementations collect type/format errors into the provided list.
 *
 * @param <T> the DTO type produced from a single CSV row
 */
public interface CsvRowMapper<T> {

    /**
     * @param row    header → raw string value for a single data row
     * @param rowNum 1-based row number (excluding the header row)
     * @param errors collector for any type/format errors found in this row
     * @return mapped DTO, or {@code null} if the row is so broken that mapping is impossible
     */
    T map(Map<String, String> row, int rowNum, List<ValidationErrorDetail> errors);
}
