package com.dbs.mot.grc.csv;

import org.springframework.web.multipart.MultipartFile;

/**
 * The upload half of {@link OrlConfigImporter} — the only methods
 * {@link com.dbs.mot.grc.service.OrlConfigurationService} and the controller's upload path need.
 * Split out from {@link ConfigExporter} so each side depends only on the methods it calls.
 */
public interface ConfigUploader {

    /**
     * Stable identifier for this configuration, used in upload-audit rows and log lines
     * (e.g. {@code "biz-units"}). Not part of any URL.
     */
    String configName();

    /**
     * Parses, validates and persists the uploaded CSV in a single transaction.
     *
     * @param file     the uploaded multipart CSV file
     * @param username value from the {@code X-EGRC-UserId} request header (stored as CREATED_BY)
     * @return number of rows imported
     */
    int processAndImport(MultipartFile file, String username);
}
