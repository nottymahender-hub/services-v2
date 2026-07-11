package com.dbs.mot.grc.common.csv;

import com.opencsv.CSVWriter;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contract for a single static-table CSV import / export handler.
 *
 * <p><b>To add support for a new table</b>, create a Spring {@code @Component}
 * that implements this interface. The {@link CsvHandlerRegistry} auto-discovers
 * all implementations and the {@link com.dbs.mot.grc.common.controller.CsvController}
 * dispatches to the correct one based on the {@code tableName} URL segment.
 * No controller changes are required.
 *
 * <p>Typical implementation structure:
 * <pre>
 *   &#64;Component
 *   class MyTableCsvHandler implements CsvHandler {
 *       &#64;Override public String getTableName() { return "my-table"; }
 *       // ... other methods
 *   }
 * </pre>
 * This registers the handler under {@code /api/csv/my-table/upload} and
 * {@code /api/csv/my-table/download} automatically.
 */
public interface CsvHandler {

    /**
     * URL path segment that identifies this handler.
     * Must be unique across all registered handlers.
     * Example: {@code "biz-units"} → {@code /api/csv/biz-units/upload}
     */
    String getTableName();

    /**
     * Filename used in the {@code Content-Disposition} download header.
     */
    String getDownloadFilename();

    /**
     * CSV column names written on the first line of the download file.
     */
    String[] getDownloadHeaders();

    /**
     * Parses, validates, and persists the uploaded CSV in a single transaction.
     *
     * @param file     the uploaded multipart CSV file
     * @param username value from the {@code X-EGRC-UserId} request header (stored as CREATED_BY)
     * @return number of rows successfully inserted
     */
    int processAndImport(MultipartFile file, String username);

    /**
     * Writes all table rows to the given {@link CSVWriter} for download.
     * The header line is written by the controller before this method is called.
     *
     * @param writer an open, ready-to-write CSVWriter
     */
    void writeDataRows(CSVWriter writer);
}
