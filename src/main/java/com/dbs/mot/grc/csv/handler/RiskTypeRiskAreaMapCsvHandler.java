package com.dbs.mot.grc.csv.handler;

import com.dbs.mot.grc.common.csv.CsvHandler;
import com.dbs.mot.grc.common.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.mapper.RiskTypeRiskAreaMapCsvRowMapper;
import com.dbs.mot.grc.csv.validator.RiskTypeRiskAreaMapCsvRowValidator;
import com.dbs.mot.grc.dto.RiskTypeRiskAreaMapCsvRow;
import com.dbs.mot.grc.entity.OrlRiskTypeRiskAreaMap;
import com.dbs.mot.grc.repository.OrlRiskTypeRiskAreaMapRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Handles CSV upload/download for {@code orl_risk_type_risk_area_map}.
 * Registered as {@code "risk-type-risk-area-maps"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskTypeRiskAreaMapCsvHandler implements CsvHandler {

    static final String TABLE_NAME = "risk-type-risk-area-maps";
    static final String[] EXPECTED_HEADERS = {"RISK_AREA", "RISK_TYPE_L4_NUM", "RISK_TYPE_L4_NM"};
    private static final String[] DOWNLOAD_HEADERS = {
            "ID", "RISK_AREA", "RISK_TYPE_L4_NUM", "RISK_TYPE_L4_NM", "CREATED_BY", "CREATED_DT_TM"
    };
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvImportProcessor csvImportProcessor;
    private final RiskTypeRiskAreaMapCsvRowMapper rowMapper;
    private final RiskTypeRiskAreaMapCsvRowValidator rowValidator;
    private final OrlRiskTypeRiskAreaMapRepository repository;
    private final JdbcAggregateOperations jdbcAggregateOperations;

    @Override
    public String getTableName()         { return TABLE_NAME; }
    @Override
    public String getDownloadFilename()  { return "orl_risk_type_risk_area_map.csv"; }
    @Override
    public String[] getDownloadHeaders() { return DOWNLOAD_HEADERS; }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        log.debug("Processing orl_risk_type_risk_area_map CSV upload for user '{}'", username);

        List<RiskTypeRiskAreaMapCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        LocalDateTime now = LocalDateTime.now();
        List<OrlRiskTypeRiskAreaMap> entities = rows.stream()
                .map(r -> OrlRiskTypeRiskAreaMap.builder()
                        .riskArea(r.getRiskArea())
                        .riskTypeL4Num(r.getRiskTypeL4Num())
                        .riskTypeL4Nm(r.getRiskTypeL4Nm())
                        .createdBy(username)
                        .createDtTm(now)
                        .build())
                .toList();

        jdbcAggregateOperations.insertAll(entities);
        log.info("Imported {} orl_risk_type_risk_area_map row(s) by '{}'", entities.size(), username);
        return entities.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        log.debug("Writing orl_risk_type_risk_area_map rows for download");
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getId()), r.getRiskArea(), s(r.getRiskTypeL4Num()),
                        r.getRiskTypeL4Nm(), r.getCreatedBy(), fmt(r.getCreateDtTm())
                }));
    }

    private String s(Object v) { return v == null ? "" : v.toString(); }

    private String fmt(LocalDateTime dt) { return dt == null ? "" : dt.format(DT_FMT); }
}
