package com.dbs.mot.grc.csv;

import com.opencsv.CSVWriter;
import org.springframework.web.multipart.MultipartFile;

/**
 * Contract for importing and exporting one ORL configuration table.
 *
 * <p>The upload payload happens to be CSV, but each implementation represents a
 * <em>configuration</em> concern (business units, entity master, scoring bands, …), not a
 * CSV concern — hence the name. Each table has its own {@code *ConfigImportService}
 * implementation in the service layer, wired explicitly into
 * {@link com.dbs.mot.grc.controller.OrlConfigurationController}.
 */
public interface OrlConfigImporter {

    /**
     * Stable identifier for this configuration, used in upload-audit rows and log lines
     * (e.g. {@code "biz-units"}). Not part of any URL.
     */
    String configName();

    /** Filename used in the {@code Content-Disposition} download header. */
    String getDownloadFilename();

    /** CSV column names written on the first line of the download file. */
    String[] getDownloadHeaders();

    /**
     * Parses, validates and persists the uploaded CSV in a single transaction.
     *
     * @param file     the uploaded multipart CSV file
     * @param username value from the {@code X-EGRC-UserId} request header (stored as CREATED_BY)
     * @return number of rows imported
     */
    int processAndImport(MultipartFile file, String username);

    /**
     * Writes all table rows to {@code writer} for download. The header line is written by the
     * controller before this call.
     */
    void writeDataRows(CSVWriter writer);
}
