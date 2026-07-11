package com.dbs.mot.grc.common.controller;

import com.dbs.mot.grc.common.csv.CsvHandler;
import com.dbs.mot.grc.common.csv.CsvHandlerRegistry;
import com.dbs.mot.grc.common.csv.CsvUploadAuditService;
import com.dbs.mot.grc.common.dto.ApiResponse;
import com.opencsv.CSVWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Single generic controller for CSV upload / download across all static tables.
 *
 * <pre>
 *   POST /api/csv/{tableName}/upload   – import CSV (multipart/form-data)
 *   GET  /api/csv/{tableName}/download – export table as CSV
 * </pre>
 *
 * <p>The {@code tableName} path variable is resolved to a {@link CsvHandler}
 * via {@link CsvHandlerRegistry}. Adding a new table requires only a new
 * {@code @Component} implementing {@link CsvHandler} — this class never changes.
 *
 * <p><b>Authentication:</b> the {@code X-EGRC-UserId} HTTP header is required for
 * upload. A missing or blank header returns HTTP 401 Unauthorized.
 *
 * <p>Every successful upload is still recorded via {@link CsvUploadAuditService}
 * (see {@code orl_static_data_maintianance_csv_upload_audit}) — there is just no longer
 * a listing API for that history.
 */
@Slf4j
@RestController
@RequestMapping("/api/csv")
@RequiredArgsConstructor
@CrossOrigin
public class CsvController {

    private final CsvHandlerRegistry registry;
    private final CsvUploadAuditService auditService;

    // =========================================================================
    // Upload
    // =========================================================================

    /**
     * Uploads a CSV file and inserts all rows into the corresponding table.
     *
     * <p>{@code @Transactional} so the data import and the audit-history insert
     * (see {@link CsvUploadAuditService#recordUpload}) commit as a single unit —
     * a failure in either rolls back both.
     *
     * @param tableName path segment that identifies the target table
     *                  (e.g. {@code biz-units}, {@code entity-mstr})
     * @param username  operator identity — read from the {@code X-EGRC-UserId} header;
     *                  stored as {@code CREATED_BY} in the table.
     *                  Returns <b>HTTP 401</b> when absent or blank.
     * @param file      CSV file (multipart binary field named {@code file})
     * @return HTTP 201 with count of inserted rows on success
     */
    @Operation(summary = "Upload a CSV file", description = "Validates and imports a CSV file into the table identified by tableName.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Rows imported successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad request or CSV validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "Uploaded file too large"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping(value = "/{tableName}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<ApiResponse<Void>> upload(
            @Parameter(description = "Target table identifier, e.g. 'biz-units'")
            @PathVariable("tableName") String tableName,
            @Parameter(description = "Operator identity, stored as CREATED_BY", required = true)
            @RequestHeader(value = "X-EGRC-UserId") String username,
            @Parameter(description = "CSV file to import")
            @RequestParam("file") MultipartFile file) {

        // Explicit blank check — Spring throws MissingRequestHeaderException only when
        // the header is completely absent; a blank value must be checked manually.
        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("X-EGRC-UserId header is required and must not be blank."));
        }

        CsvHandler handler = registry.getHandler(tableName);
        int count = handler.processAndImport(file, username.trim());
        auditService.recordUpload(tableName, file.getOriginalFilename(), username.trim(), count);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(count + " record(s) imported successfully into '" + tableName + "'."));
    }

    // =========================================================================
    // Download
    // =========================================================================

    /**
     * Downloads the full content of a table as a CSV file.
     *
     * @param tableName path segment that identifies the source table
     * @param response  servlet response — written directly to avoid buffering the file in memory
     */
    @Operation(summary = "Download a table as CSV", description = "Streams the full content of the table identified by tableName as a CSV file.")
    @GetMapping("/{tableName}/download")
    public void download(
            @Parameter(description = "Source table identifier, e.g. 'biz-units'")
            @PathVariable("tableName") String tableName,
            HttpServletResponse response) throws Exception {

        CsvHandler handler = registry.getHandler(tableName);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + handler.getDownloadFilename() + "\"");

        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {

            writer.writeNext(handler.getDownloadHeaders());
            handler.writeDataRows(writer);
        }
    }
}
