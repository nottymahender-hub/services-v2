package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.ConfigUploader;
import com.dbs.mot.grc.csv.CsvUploadAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Orchestrates an ORL configuration upload: runs the table-specific import and records the upload
 * audit row in a single transaction, so the data import and its audit entry commit atomically.
 *
 * <p>Keeping this here (rather than in the controller) leaves the controller thin and confines
 * transaction management to the service layer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrlConfigurationService {

    private final CsvUploadAuditService auditService;

    /**
     * Imports one configuration file and records the audit row atomically.
     *
     * @param importer         the table-specific uploader
     * @param file             the uploaded CSV
     * @param username         operator identity (already validated/trimmed)
     * @param originalFilename the uploaded file's original name (for the audit row)
     * @return number of rows imported
     */
    @Transactional
    public int importConfig(ConfigUploader importer, MultipartFile file,
                            String username, String originalFilename) {
        int count = importer.processAndImport(file, username);
        auditService.recordUpload(importer.configName(), originalFilename, username, count);
        log.info("Imported {} row(s) into '{}' by '{}'", count, importer.configName(), username);
        return count;
    }
}
