package com.dbs.mot.grc.common.csv;

import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.common.exception.BadRequestException;
import com.dbs.mot.grc.common.exception.CsvValidationException;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Reusable CSV processing template used by every table handler.
 *
 * <p>Pipeline per upload request:
 * <ol>
 *   <li>File-level validation (empty, wrong type)</li>
 *   <li>Header validation (expected columns present, <b>no duplicate columns</b>)</li>
 *   <li>Per-row mapping via {@link CsvRowMapper}</li>
 *   <li>Per-row Bean Validation (Hibernate Validator) for size/format constraints</li>
 *   <li>Dataset-level validation via {@link CsvRowValidator} (uniqueness, refs)</li>
 * </ol>
 *
 * <p>Adding a new table requires only a new {@link CsvRowMapper} + {@link CsvRowValidator}
 * implementation — no changes here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsvImportProcessor {

    private static final String CSV_CONTENT_TYPE = "text/csv";

    /**
     * Hibernate Validator — validates Bean Validation annotations on DTOs.
     */
    private final Validator validator;

    /**
     * Validates the file, parses it, runs per-row mapping + Hibernate validation,
     * then dataset-level validation.
     *
     * @param file            uploaded multipart file
     * @param expectedHeaders ordered expected column names (case-sensitive)
     * @param rowMapper       converts one CSV row into a DTO
     * @param rowValidator    cross-row validation (uniqueness, references, etc.)
     * @param <T>             DTO type
     * @return validated, mapped rows ready for persistence
     * @throws BadRequestException    for file-level problems (empty, wrong type, bad headers)
     * @throws CsvValidationException when one or more rows fail domain validation
     */
    public <T> List<T> process(
            MultipartFile file,
            String[] expectedHeaders,
            CsvRowMapper<T> rowMapper,
            CsvRowValidator<T> rowValidator) {

        validateFile(file);

        List<T> rows = new ArrayList<>();
        List<ValidationErrorDetail> mappingErrors = new ArrayList<>();

        try (CSVReader reader = buildCsvReader(file)) {

            // --- header row ---
            String[] headers = reader.readNext();
            if (headers == null) {
                throw new BadRequestException("The uploaded CSV file is empty (no header row found).");
            }
            validateHeaders(headers, expectedHeaders);

            // --- data rows ---
            String[] rawRow;
            int rowNum = 0;
            while ((rawRow = reader.readNext()) != null) {
                rowNum++;
                Map<String, String> rowMap = buildRowMap(headers, rawRow);

                T dto = rowMapper.map(rowMap, rowNum, mappingErrors);
                if (dto != null) {
                    // Run Hibernate Bean Validation (e.g. @Size, @Pattern) on the mapped DTO
                    runBeanValidation(dto, rowNum, mappingErrors);
                    rows.add(dto);
                }
            }

            if (rowNum == 0) {
                throw new BadRequestException("The uploaded CSV file has a header row but no data rows.");
            }

        } catch (BadRequestException | CsvValidationException e) {
            throw e;
        } catch (IOException | com.opencsv.exceptions.CsvValidationException e) {
            // Malformed/unreadable upload — a client-side problem, so return 400 rather than 500.
            log.error("Unexpected error while reading CSV", e);
            throw new BadRequestException("Failed to read CSV file: " + e.getMessage());
        }

        // Fail fast on mapping / Bean Validation errors before running cross-row checks
        if (!mappingErrors.isEmpty()) {
            throw new CsvValidationException(mappingErrors);
        }

        // Cross-row validation (duplicates, referential integrity within the CSV, etc.)
        List<ValidationErrorDetail> datasetErrors = rowValidator.validate(rows);
        if (!datasetErrors.isEmpty()) {
            throw new CsvValidationException(datasetErrors);
        }

        return rows;
    }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    /**
     * Validates that the uploaded file is present, non-empty, and has a CSV content type or name.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file is empty or missing.");
        }

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        boolean csvByType = contentType != null &&
                (contentType.equalsIgnoreCase(CSV_CONTENT_TYPE) ||
                        contentType.equalsIgnoreCase("text/plain") ||
                        contentType.equalsIgnoreCase("application/vnd.ms-excel") ||
                        contentType.equalsIgnoreCase("application/octet-stream"));

        boolean csvByName = filename != null && filename.toLowerCase().endsWith(".csv");

        if (!csvByType && !csvByName) {
            throw new BadRequestException(
                    "Invalid file type. Only CSV files are accepted. Received content-type: " + contentType);
        }
    }

    private CSVReader buildCsvReader(MultipartFile file) throws IOException {
        return new CSVReaderBuilder(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                .withCSVParser(
                        new CSVParserBuilder()
                                .withSeparator(',')
                                .withQuoteChar('"')
                                .withIgnoreLeadingWhiteSpace(true)
                                .build())
                .build();
    }

    /**
     * Validates that:
     * <ul>
     *   <li>The actual headers match the expected set (no missing, no extra).</li>
     *   <li><b>No column name appears more than once</b> in the uploaded file.</li>
     * </ul>
     */
    private void validateHeaders(String[] actual, String[] expected) {
        // Strip BOM and whitespace that editors sometimes prepend
        String[] cleaned = Arrays.stream(actual)
                .map(h -> h.trim().replace("﻿", ""))
                .toArray(String[]::new);

        // ── Duplicate column check ──────────────────────────────────────────
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String h : cleaned) {
            if (!seen.add(h)) {
                duplicates.add(h);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new BadRequestException(
                    "CSV contains duplicate column header(s): " + duplicates
                            + ". Each column name must appear exactly once.");
        }

        // ── Expected vs actual set check ────────────────────────────────────
        Set<String> actualSet = new HashSet<>(Arrays.asList(cleaned));
        Set<String> expectedSet = new HashSet<>(Arrays.asList(expected));

        if (!actualSet.equals(expectedSet)) {
            List<String> missing = expectedSet.stream()
                    .filter(h -> !actualSet.contains(h)).sorted().toList();
            List<String> extra = actualSet.stream()
                    .filter(h -> !expectedSet.contains(h)).sorted().toList();
            throw new BadRequestException(
                    "CSV headers do not match expected columns."
                            + (missing.isEmpty() ? "" : " Missing: " + missing + ".")
                            + (extra.isEmpty() ? "" : " Unexpected: " + extra + "."));
        }
    }

    private Map<String, String> buildRowMap(String[] headers, String[] values) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String key = headers[i].trim().replace("﻿", "");
            String value = (i < values.length) ? values[i] : "";
            map.put(key, value == null ? "" : value.trim());
        }
        return map;
    }

    /**
     * Runs Hibernate Bean Validation on the mapped DTO.
     * Used to enforce {@code @Size}, {@code @Pattern} and other constraints
     * declared on DTO fields, providing a second layer of defence beyond the
     * type-conversion checks already done in the row mapper.
     */
    private <T> void runBeanValidation(T dto, int rowNum, List<ValidationErrorDetail> errors) {
        Set<ConstraintViolation<T>> violations = validator.validate(dto);
        for (ConstraintViolation<T> v : violations) {
            errors.add(ValidationErrorDetail.builder()
                    .row(rowNum)
                    .field(v.getPropertyPath().toString())
                    .message(v.getMessage())
                    .build());
        }
    }
}
