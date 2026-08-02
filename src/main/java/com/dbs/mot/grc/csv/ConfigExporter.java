package com.dbs.mot.grc.csv;

import com.opencsv.CSVWriter;

/**
 * The download half of {@link OrlConfigImporter} — the only methods
 * {@link OrlConfigImporterRegistry} and the controller's download path need. Split out from
 * {@link ConfigUploader} so each side depends only on the methods it calls.
 */
public interface ConfigExporter {

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
     * Writes all table rows to {@code writer} for download. The header line is written by the
     * controller before this call.
     */
    void writeDataRows(CSVWriter writer);
}
