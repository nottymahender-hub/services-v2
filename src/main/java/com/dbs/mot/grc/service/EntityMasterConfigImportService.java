package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.dbs.mot.grc.csv.mapper.EntityMstrCsvRowMapper;
import com.dbs.mot.grc.csv.validator.EntityMstrCsvRowValidator;
import com.dbs.mot.grc.dto.EntityMstrCsvRow;
import com.dbs.mot.grc.entity.OrlEntityMstr;
import com.dbs.mot.grc.repository.OrlEntityMstrRepository;
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
 * Import/export of the {@code orl_entity_mstr} ORL configuration.
 *
 * <p>Upload is an upsert keyed on the client-supplied {@code ENTITY_NUM}. Rather than a
 * database-native {@code ON DUPLICATE KEY UPDATE}, it stays repository-only and batched: the
 * existing rows are read once, incoming rows are split into inserts and updates, and each set is
 * persisted with a single {@code saveAll(...)}. Within-file duplicates keep the last occurrence
 * (matching upsert "last wins"); audit columns are preserved on update.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityMasterConfigImportService implements OrlConfigImporter {

    static final String CONFIG_NAME = "entity-mstr";
    static final String[] EXPECTED_HEADERS = {"ENTITY_NUM", "ENTITY_NM", "orl_location", "orl_location_ic"};
    private static final String[] DOWNLOAD_HEADERS = {
            "ENTITY_NUM", "ENTITY_NM", "orl_location", "orl_location_ic", "uploadedBy", "uploadedAt"
    };

    private final CsvImportProcessor csvImportProcessor;
    private final EntityMstrCsvRowMapper rowMapper;
    private final EntityMstrCsvRowValidator rowValidator;
    private final OrlEntityMstrRepository repository;

    @Override
    public String configName() {
        return CONFIG_NAME;
    }

    @Override
    public String getDownloadFilename() {
        return "orl_entity_mstr.csv";
    }

    @Override
    public String[] getDownloadHeaders() {
        return DOWNLOAD_HEADERS;
    }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        log.debug("Importing {} config for user '{}'", CONFIG_NAME, username);

        List<EntityMstrCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        // De-duplicate within the file keeping the last occurrence (upsert "last wins").
        Map<Integer, EntityMstrCsvRow> byKey = new LinkedHashMap<>();
        rows.forEach(r -> byKey.put(r.getEntityNum(), r));

        Map<Integer, OrlEntityMstr> existing = new HashMap<>();
        repository.findAllById(byKey.keySet()).forEach(e -> existing.put(e.getEntityNum(), e));

        // Timestamp columns are DB-managed (CREATED_DT_TM default on insert, UPDATE_DT_TM
        // ON UPDATE on update); the update path only carries CREATED_BY forward.
        List<OrlEntityMstr> toInsert = new ArrayList<>();
        List<OrlEntityMstr> toUpdate = new ArrayList<>();
        byKey.values().forEach(r -> {
            OrlEntityMstr prior = existing.get(r.getEntityNum());
            OrlEntityMstr.OrlEntityMstrBuilder builder = OrlEntityMstr.builder()
                    .entityNum(r.getEntityNum())
                    .entityNm(r.getEntityNm())
                    .orlLocation(r.getOrlLocation() == null ? "" : r.getOrlLocation())
                    .orlLocationIc(r.getOrlLocationIc());
            if (prior == null) {
                toInsert.add(builder.createdBy(username).newRecord(true).build());
            } else {
                toUpdate.add(builder
                        .createdBy(prior.getCreatedBy())
                        .updatedBy(username).newRecord(false).build());
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
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(r ->
                writer.writeNext(new String[]{
                        CsvFormatters.cell(r.getEntityNum()), r.getEntityNm(),
                        r.getOrlLocation(), r.getOrlLocationIc(),
                        StringUtils.isBlank(r.getUpdatedBy()) ? r.getCreatedBy() : r.getUpdatedBy(),
                        CsvFormatters.cell(Objects.isNull(r.getUpdateDtTm()) ? r.getCreatedDtTm() : r.getUpdateDtTm())
                }));
    }
}
