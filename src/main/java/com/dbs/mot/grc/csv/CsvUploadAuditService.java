package com.dbs.mot.grc.csv;

import com.dbs.mot.grc.entity.CsvUploadAudit;
import com.dbs.mot.grc.repository.CsvUploadAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records one audit row per successful configuration upload.
 *
 * <p>{@link #recordUpload} is called by
 * {@link com.dbs.mot.grc.service.OrlConfigurationService#importConfig} immediately after the
 * table's import returns. That method is {@code @Transactional}, so the data import and this
 * audit insert commit atomically — if either fails, both roll back. {@code recordUpload} is
 * therefore {@code MANDATORY}: it must run inside that transaction. Centralising it here keeps
 * the cross-cutting "record every upload" concern in exactly one place.
 */
@Service
@RequiredArgsConstructor
public class CsvUploadAuditService {

    private final CsvUploadAuditRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUpload(String tableName, String fileName, String uploadedBy, int rowCount) {
        // uploaded_dt_tm is filled by the DB default on insert — not set here.
        CsvUploadAudit audit = CsvUploadAudit.builder()
                .fileName(fileName == null ? "unknown.csv" : fileName)
                .tableName(tableName)
                .uploadedBy(uploadedBy)
                .rowCount(rowCount)
                .status("SUCCESS")
                .build();
        repository.save(audit);
    }
}
