package com.dbs.mot.grc.csv.handler;

import com.dbs.mot.grc.common.csv.CsvHandler;
import com.dbs.mot.grc.common.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.mapper.OrlLndscpDimCsvRowMapper;
import com.dbs.mot.grc.csv.validator.OrlLndscpDimCsvRowValidator;
import com.dbs.mot.grc.dto.OrlLndscpDimCsvRow;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.repository.OrlLndscpDimRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Handles CSV upload/download for {@code orl_lndscp_dim}.
 * Registered as {@code "lndscp-dim"} → /api/csv/lndscp-dim/upload|download.
 *
 * <p>RISK_AREA is a JSON string (e.g. {@code {"Cyber Risk": ["OR"]}}).
 * On re-upload the VERSION is auto-advanced to avoid unique-index conflicts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrlLndscpDimCsvHandler implements CsvHandler {

    static final String TABLE_NAME = "lndscp-dim";
    static final String[] EXPECTED_HEADERS = {
            "CONFIG_ID", "LNDSCP_NM", "EFFECT_START_DT", "EFFECT_END_DT",
            "RISK_AREA", "BIZ_UNITS", "BIZ_UNIT_LVL", "LOCATIONS"
    };
    private static final String[] DOWNLOAD_HEADERS = {
            "id", "CONFIG_ID", "LNDSCP_NM", "EFFECT_START_DT", "EFFECT_END_DT",
            "VERSION", "STATUS", "RISK_AREA", "BIZ_UNITS", "BIZ_UNIT_LVL",
            "LOCATIONS", "CREATED_BY", "CREATE_DT_TM"
    };
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvImportProcessor csvImportProcessor;
    private final OrlLndscpDimCsvRowMapper rowMapper;
    private final OrlLndscpDimCsvRowValidator rowValidator;
    private final OrlLndscpDimRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final JdbcAggregateOperations jdbcAggregateOperations;

    @Override public String getTableName()         { return TABLE_NAME; }
    @Override public String getDownloadFilename()  { return "orl_lndscp_dim.csv"; }
    @Override public String[] getDownloadHeaders() { return DOWNLOAD_HEADERS; }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        log.debug("Processing orl_lndscp_dim CSV upload for user '{}'", username);

        List<OrlLndscpDimCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        List<String> configIds = rows.stream()
                .map(OrlLndscpDimCsvRow::getConfigId)
                .toList();
        int version = resolveVersion(configIds);
        log.debug("Resolved VERSION={} for {} CONFIG_ID(s)", version, configIds.size());

        LocalDateTime now = LocalDateTime.now();
        List<OrlLndscpDim> entities = rows.stream()
                .map(r -> OrlLndscpDim.builder()
                        .configId(r.getConfigId())
                        .lndscpNm(r.getLndscpNm())
                        .effectStartDt(r.getEffectStartDt())
                        .effectEndDt(r.getEffectEndDt())
                        .version(version)
                        .status("ACTIVE")
                        .riskArea(r.getRiskArea())
                        .bizUnits(r.getBizUnits())
                        .bizUnitLvl(r.getBizUnitLvl())
                        .locations(r.getLocations())
                        .createdBy(username)
                        .createDtTm(now)
                        .build())
                .toList();

        jdbcAggregateOperations.insertAll(entities);
        log.info("Imported {} orl_lndscp_dim row(s) (version={}) by '{}'",
                entities.size(), version, username);
        return entities.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        log.debug("Writing orl_lndscp_dim rows for download");
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getId()), r.getConfigId(), r.getLndscpNm(),
                        s(r.getEffectStartDt()), s(r.getEffectEndDt()),
                        s(r.getVersion()), r.getStatus(),
                        r.getRiskArea(), s(r.getBizUnits()), s(r.getBizUnitLvl()),
                        r.getLocations(), r.getCreatedBy(), fmt(r.getCreateDtTm())
                }));
    }

    int resolveVersion(List<String> configIds) {
        if (configIds == null || configIds.isEmpty()) return 1;
        String placeholders = configIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT COALESCE(MAX(VERSION), 0) FROM orl_lndscp_dim WHERE CONFIG_ID IN ("
                + placeholders + ")";
        Integer maxVersion = jdbcTemplate.queryForObject(sql, Integer.class, configIds.toArray());
        int resolved = (maxVersion != null && maxVersion > 0) ? maxVersion + 1 : 1;
        log.debug("Version resolution: maxExisting={} resolved={}", maxVersion, resolved);
        return resolved;
    }

    private String s(Object v) { return v == null ? "" : v.toString(); }

    private String fmt(LocalDateTime dt) { return dt == null ? "" : dt.format(DT_FMT); }
}
