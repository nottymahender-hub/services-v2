package com.dbs.mot.grc.common.csv;

import com.dbs.mot.grc.entity.CsvUploadAudit;
import com.dbs.mot.grc.repository.CsvUploadAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Records one audit row per successful CSV upload.
 *
 * <p>{@link #recordUpload} is called by {@link com.dbs.mot.grc.common.controller.CsvController}
 * immediately after {@link CsvHandler#processAndImport} returns successfully. Both calls run
 * inside the same request; {@code CsvController.upload} is {@code @Transactional} so the data
 * insert and the audit insert commit atomically as a single unit — if either fails, both roll
 * back. Doing it this way (rather than pushing the audit insert into each individual handler)
 * keeps the cross-cutting "record every upload" concern in exactly one place.
 */
@Service
@RequiredArgsConstructor
public class CsvUploadAuditService {

    private final CsvUploadAuditRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUpload(String tableName, String fileName, String uploadedBy, int rowCount) {
        CsvUploadAudit audit = CsvUploadAudit.builder()
                .fileName(fileName == null ? "unknown.csv" : fileName)
                .tableName(tableName)
                .uploadedBy(uploadedBy)
                .uploadedDtTm(LocalDateTime.now())
                .rowCount(rowCount)
                .status("SUCCESS")
                .build();
        repository.save(audit);
    }
}
