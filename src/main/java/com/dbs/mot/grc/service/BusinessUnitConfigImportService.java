package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.dbs.mot.grc.csv.mapper.BizUnitCsvRowMapper;
import com.dbs.mot.grc.csv.validator.BizUnitCsvRowValidator;
import com.dbs.mot.grc.dto.BizUnitCsvRow;
import com.dbs.mot.grc.entity.OrlBizUnit;
import com.dbs.mot.grc.repository.OrlBizUnitRepository;
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
 * Import/export of the {@code orl_biz_unit} ORL configuration.
 *
 * <p>Upload is an upsert keyed on the client-supplied {@code BU_NUM}, implemented repository-only
 * and batched: existing rows are read once, incoming rows split into inserts/updates, each set
 * persisted with one {@code saveAll(...)}. Within-file duplicates keep the last occurrence; audit
 * columns are preserved on update.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessUnitConfigImportService implements OrlConfigImporter {

    static final String CONFIG_NAME = "biz-units";
    static final String[] EXPECTED_HEADERS = {
            "BU_NUM", "BU_NM", "LVL_OF_HIER", "ORL_BU_NM_L2", "ORL_BU_NM_L3", "ORL_BU_NM_L4", "BU_FULL_PATH"
    };
    private static final String[] DOWNLOAD_HEADERS = {
            "BU_NUM", "BU_NM", "LVL_OF_HIER", "ORL_BU_NM_L2", "ORL_BU_NM_L3",
            "ORL_BU_NM_L4", "BU_FULL_PATH", "uploadedBy", "uploadedAt"
    };

    private final CsvImportProcessor csvImportProcessor;
    private final BizUnitCsvRowMapper rowMapper;
    private final BizUnitCsvRowValidator rowValidator;
    private final OrlBizUnitRepository repository;

    @Override
    public String configName() {
        return CONFIG_NAME;
    }

    @Override
    public String getDownloadFilename() {
        return "orl_biz_unit.csv";
    }

    @Override
    public String[] getDownloadHeaders() {
        return DOWNLOAD_HEADERS;
    }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        log.debug("Importing {} config for user '{}'", CONFIG_NAME, username);

        List<BizUnitCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        Map<Integer, BizUnitCsvRow> byKey = new LinkedHashMap<>();
        rows.forEach(r -> byKey.put(r.getBuNum(), r));

        Map<Integer, OrlBizUnit> existing = new HashMap<>();
        repository.findAllById(byKey.keySet()).forEach(e -> existing.put(e.getBuNum(), e));

        // Timestamp columns are DB-managed (CREATE_DT_TM default on insert, UPDATE_DT_TM
        // ON UPDATE on update); the update path only carries CREATED_BY forward.
        List<OrlBizUnit> toInsert = new ArrayList<>();
        List<OrlBizUnit> toUpdate = new ArrayList<>();
        byKey.values().forEach(r -> {
            OrlBizUnit prior = existing.get(r.getBuNum());
            OrlBizUnit.OrlBizUnitBuilder builder = OrlBizUnit.builder()
                    .buNum(r.getBuNum())
                    .buNm(r.getBuNm())
                    .lvlOfHier(r.getLvlOfHier())
                    .orlBuNmL2(r.getOrlBuNmL2())
                    .orlBuNmL3(r.getOrlBuNmL3())
                    .orlBuNmL4(r.getOrlBuNmL4())
                    .buFullPath(r.getBuFullPath());
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
                        CsvFormatters.cell(r.getBuNum()), r.getBuNm(), CsvFormatters.cell(r.getLvlOfHier()),
                        r.getOrlBuNmL2(), r.getOrlBuNmL3(), r.getOrlBuNmL4(), r.getBuFullPath(),
                        StringUtils.isBlank(r.getUpdatedBy()) ? r.getCreatedBy() : r.getUpdatedBy(),
                        CsvFormatters.cell(Objects.isNull(r.getUpdateDtTm()) ? r.getCreateDtTm() : r.getUpdateDtTm())
                }));
    }
}
