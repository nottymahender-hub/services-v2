package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.dbs.mot.grc.csv.mapper.BuLctnHeadcountCsvRowMapper;
import com.dbs.mot.grc.csv.validator.BuLctnHeadcountCsvRowValidator;
import com.dbs.mot.grc.dto.BuLctnHeadcountCsvRow;
import com.dbs.mot.grc.entity.OrlBuLctnHeadcount;
import com.dbs.mot.grc.repository.OrlBuLctnHeadcountRepository;
import com.dbs.mot.grc.util.CsvFormatters;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.StreamSupport;

/**
 * Import/export of the {@code orl_bu_loctn_headcount} ORL configuration.
 *
 * <p>Upload is an upsert whose conflict key is the composite unique index
 * {@code (ORL_BU_NM_L2, ORL_BU_NM_L3, ORL_BU_NM_L4, location)}, not the surrogate {@code id}.
 * It stays repository-only and batched: existing rows are read and indexed by that composite key,
 * incoming rows are split into inserts (new id) and updates (existing id reused), and each set is
 * persisted with one {@code saveAll(...)}. Within-file duplicates keep the last occurrence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuLocationHeadcountConfigImportService implements OrlConfigImporter {

    static final String CONFIG_NAME = "bu-loctn-headcount";
    static final String[] EXPECTED_HEADERS = {
            "ORL_BU_NM_L2", "ORL_BU_NM_L3", "ORL_BU_NM_L4", "location", "headcount"
    };
    private static final String[] DOWNLOAD_HEADERS = {
            "id", "ORL_BU_NM_L2", "ORL_BU_NM_L3", "ORL_BU_NM_L4",
            "location", "headcount", "uploadedBy", "uploadedAt"
    };
    private static final String KEY_DELIM = "\u0001";

    private final CsvImportProcessor csvImportProcessor;
    private final BuLctnHeadcountCsvRowMapper rowMapper;
    private final BuLctnHeadcountCsvRowValidator rowValidator;
    private final OrlBuLctnHeadcountRepository repository;

    @Override
    public String configName() {
        return CONFIG_NAME;
    }

    @Override
    public String getDownloadFilename() {
        return "orl_bu_loctn_headcount.csv";
    }

    @Override
    public String[] getDownloadHeaders() {
        return DOWNLOAD_HEADERS;
    }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        log.debug("Importing {} config for user '{}'", CONFIG_NAME, username);

        List<BuLctnHeadcountCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        // De-duplicate within the file by composite key keeping the last occurrence.
        Map<String, BuLctnHeadcountCsvRow> byKey = new LinkedHashMap<>();
        rows.forEach(r -> byKey.put(
                compositeKey(r.getOrlBuNmL2(), r.getOrlBuNmL3(), r.getOrlBuNmL4(), r.getLocation()), r));

        // Index existing rows by the same composite key so updates reuse the surrogate id.
        Map<String, OrlBuLctnHeadcount> existing = new HashMap<>();
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(e -> existing.put(
                compositeKey(e.getOrlBuNmL2(), e.getOrlBuNmL3(), e.getOrlBuNmL4(), e.getLocation()), e));

        // Timestamp columns are DB-managed (CREATE_DT_TM default on insert, UPDATE_DT_TM
        // ON UPDATE on update); the update path only carries CREATED_BY forward.
        List<OrlBuLctnHeadcount> toInsert = new ArrayList<>();
        List<OrlBuLctnHeadcount> toUpdate = new ArrayList<>();
        byKey.forEach((key, r) -> {
            OrlBuLctnHeadcount prior = existing.get(key);
            OrlBuLctnHeadcount.OrlBuLctnHeadcountBuilder builder = OrlBuLctnHeadcount.builder()
                    .orlBuNmL2(r.getOrlBuNmL2())
                    .orlBuNmL3(r.getOrlBuNmL3())
                    .orlBuNmL4(r.getOrlBuNmL4())
                    .location(r.getLocation())
                    .headcount(r.getHeadcount());
            if (prior == null) {
                // id left null → Spring Data JDBC inserts (auto-generated key).
                toInsert.add(builder.createdBy(username).build());
            } else {
                // id set → Spring Data JDBC updates the existing row.
                toUpdate.add(builder.id(prior.getId())
                        .createdBy(prior.getCreatedBy())
                        .updatedBy(username).build());
            }
        });

        if (!toInsert.isEmpty()) {
            repository.saveAll(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            repository.saveAll(toUpdate);
        }
        log.info("Upserted {} {} row(s) ({} inserted, {} updated) by '{}'",
                byKey.size(), CONFIG_NAME, toInsert.size(), toUpdate.size(), username);
        return rows.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        log.debug("Writing {} rows for download", CONFIG_NAME);
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(r ->
                writer.writeNext(new String[]{
                        CsvFormatters.cell(r.getId()), r.getOrlBuNmL2(), CsvFormatters.cell(r.getOrlBuNmL3()), CsvFormatters.cell(r.getOrlBuNmL4()),
                        r.getLocation(), CsvFormatters.cell(r.getHeadcount()),
                        StringUtils.isBlank(r.getUpdatedBy()) ? r.getCreatedBy() : r.getUpdatedBy(),
                        CsvFormatters.cell(Objects.isNull(r.getUpdateDtTm()) ? r.getCreateDtTm() : r.getUpdateDtTm())
                }));
    }

    /** Null-safe join of the composite unique-key columns. */
    private String compositeKey(String l2, String l3, String l4, String location) {
        return String.join(KEY_DELIM,
                l2 == null ? "" : l2,
                l3 == null ? "" : l3,
                l4 == null ? "" : l4,
                location == null ? "" : location);
    }
}
