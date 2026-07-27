package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.dbs.mot.grc.csv.mapper.RiskTypeRiskAreaMapCsvRowMapper;
import com.dbs.mot.grc.csv.validator.RiskTypeRiskAreaMapCsvRowValidator;
import com.dbs.mot.grc.dto.RiskTypeRiskAreaMapCsvRow;
import com.dbs.mot.grc.entity.OrlRiskTypeRiskAreaMap;
import com.dbs.mot.grc.repository.OrlRiskTypeRiskAreaMapRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Import/export of the {@code orl_risk_type_risk_area_map} ORL configuration.
 * Append-only: every uploaded row is inserted via the repository.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskTypeRiskAreaMapConfigImportService implements OrlConfigImporter {

    static final String CONFIG_NAME = "risk-type-risk-area-maps";
    static final String[] EXPECTED_HEADERS = {
            "RISK_AREA", "RISK_TYPE_L4_NUM", "RISK_TYPE_L4_NM", "IS_OR_FA", "RISK_CLUSTER"
    };
    private static final String[] DOWNLOAD_HEADERS = {
            "ID", "RISK_AREA", "RISK_TYPE_L4_NUM", "RISK_TYPE_L4_NM",
            "IS_OR_FA", "RISK_CLUSTER", "CREATED_BY", "CREATED_DT_TM"
    };
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvImportProcessor csvImportProcessor;
    private final RiskTypeRiskAreaMapCsvRowMapper rowMapper;
    private final RiskTypeRiskAreaMapCsvRowValidator rowValidator;
    private final OrlRiskTypeRiskAreaMapRepository repository;

    @Override
    public String configName() {
        return CONFIG_NAME;
    }

    @Override
    public String getDownloadFilename() {
        return "orl_risk_type_risk_area_map.csv";
    }

    @Override
    public String[] getDownloadHeaders() {
        return DOWNLOAD_HEADERS;
    }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        log.debug("Importing {} config for user '{}'", CONFIG_NAME, username);

        List<RiskTypeRiskAreaMapCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        // CREATED_DT_TM is filled by the DB default on insert — not set here.
        List<OrlRiskTypeRiskAreaMap> entities = rows.stream()
                .map(r -> OrlRiskTypeRiskAreaMap.builder()
                        .riskArea(r.getRiskArea())
                        .riskTypeL4Num(r.getRiskTypeL4Num())
                        .riskTypeL4Nm(r.getRiskTypeL4Nm())
                        .isOrFa(r.getIsOrFa())
                        .riskCluster(r.getRiskCluster())
                        .createdBy(username)
                        .build())
                .toList();

        repository.saveAll(entities);
        log.info("Imported {} {} row(s) by '{}'", entities.size(), CONFIG_NAME, username);
        return entities.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        log.debug("Writing {} rows for download", CONFIG_NAME);
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getId()), r.getRiskArea(), s(r.getRiskTypeL4Num()),
                        r.getRiskTypeL4Nm(), r.getIsOrFa(), r.getRiskCluster(),
                        r.getCreatedBy(), fmt(r.getCreateDtTm())
                }));
    }

    private String s(Object v) {
        return v == null ? "" : v.toString();
    }

    private String fmt(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DT_FMT);
    }
}
