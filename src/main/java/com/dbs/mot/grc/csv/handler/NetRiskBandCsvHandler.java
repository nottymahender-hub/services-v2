package com.dbs.mot.grc.csv.handler;

import com.dbs.mot.grc.common.csv.CsvHandler;
import com.dbs.mot.grc.common.csv.CsvImportProcessor;
import com.dbs.mot.grc.common.enums.Module;
import com.dbs.mot.grc.common.enums.NetRiskRating;
import com.dbs.mot.grc.common.util.ConfigVersionResolver;
import com.dbs.mot.grc.csv.mapper.NetRiskBandCsvRowMapper;
import com.dbs.mot.grc.csv.validator.NetRiskBandCsvRowValidator;
import com.dbs.mot.grc.dto.NetRiskBandCsvRow;
import com.dbs.mot.grc.entity.NetRiskBand;
import com.dbs.mot.grc.repository.NetRiskBandRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles CSV upload/download for {@code net_risk_band}. Registered as {@code "net-risk-band"}.
 *
 * <p>Download returns only the <em>latest</em> {@code config_version} row per
 * {@code (net_risk_rtng, module)} group — filtered in Java after a plain
 * {@code findAll()}, keeping the query itself unchanged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NetRiskBandCsvHandler implements CsvHandler {

    static final String TABLE_NAME = "net-risk-band";
    static final String DB_TABLE = "net_risk_band";
    static final String[] EXPECTED_HEADERS = {
            "range_low", "range_high", "net_risk_rtng", "module"
    };
    private static final List<String> GROUP_COLS = List.of("net_risk_rtng", "module");
    private static final String[] DOWNLOAD_HEADERS = {
            "id", "config_version", "range_low", "range_high", "net_risk_rtng", "module", "CREATED_BY", "CREATE_DT_TM"
    };
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvImportProcessor csvImportProcessor;
    private final NetRiskBandCsvRowMapper rowMapper;
    private final NetRiskBandCsvRowValidator rowValidator;
    private final NetRiskBandRepository repository;
    private final ConfigVersionResolver configVersionResolver;
    private final JdbcAggregateOperations jdbcAggregateOperations;

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public String getDownloadFilename() {
        return "net_risk_band.csv";
    }

    @Override
    public String[] getDownloadHeaders() {
        return DOWNLOAD_HEADERS;
    }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        List<NetRiskBandCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        Set<List<String>> groupKeys = rows.stream()
                .map(r -> List.of(r.getNetRiskRtng(), r.getModule()))
                .collect(Collectors.toSet());
        Map<String, Integer> nextVersions =
                configVersionResolver.resolveNextVersions(DB_TABLE, GROUP_COLS, groupKeys);

        LocalDateTime now = LocalDateTime.now();
        List<NetRiskBand> entities = rows.stream()
                .map(r -> {
                    String key = ConfigVersionResolver.groupKey(r.getNetRiskRtng(), r.getModule());
                    return NetRiskBand.builder()
                            .configVersion(nextVersions.get(key))
                            .rangeLow(r.getRangeLow()).rangeHigh(r.getRangeHigh())
                            .netRiskRtng(NetRiskRating.fromDbValue(r.getNetRiskRtng()))
                            .module(Module.fromDbValue(r.getModule()))
                            .createdBy(username).createDtTm(now).build();
                })
                .toList();

        jdbcAggregateOperations.insertAll(entities);
        log.info("Imported {} net_risk_band rows by '{}'", entities.size(), username);
        return entities.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        latestPerGroup().forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getId()), s(r.getConfigVersion()),
                        s(r.getRangeLow()), s(r.getRangeHigh()),
                        r.getNetRiskRtng().getDbValue(), r.getModule().getDbValue(),
                        r.getCreatedBy(), fmt(r.getCreateDtTm())
                }));
    }

    /** Reduces all rows to the highest {@code config_version} per {@code (net_risk_rtng, module)} group. */
    private Collection<NetRiskBand> latestPerGroup() {
        Map<String, NetRiskBand> latestByGroup = new LinkedHashMap<>();
        for (NetRiskBand row : repository.findAll()) {
            String key = ConfigVersionResolver.groupKey(
                    row.getNetRiskRtng().getDbValue(), row.getModule().getDbValue());
            latestByGroup.merge(key, row,
                    (existing, candidate) -> candidate.getConfigVersion() > existing.getConfigVersion()
                            ? candidate : existing);
        }
        return latestByGroup.values();
    }

    private String s(Object v) {
        return v == null ? "" : v.toString();
    }

    private String fmt(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DT_FMT);
    }
}
