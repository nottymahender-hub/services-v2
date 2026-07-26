package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.dbs.mot.grc.csv.mapper.NetRiskBandCsvRowMapper;
import com.dbs.mot.grc.csv.validator.NetRiskBandCsvRowValidator;
import com.dbs.mot.grc.dto.NetRiskBandCsvRow;
import com.dbs.mot.grc.enums.Module;
import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.entity.NetRiskBand;
import com.dbs.mot.grc.repository.NetRiskBandRepository;
import com.dbs.mot.grc.util.ConfigVersionResolver;
import com.dbs.mot.grc.util.ConfigVersionResolver.GroupMax;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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
 * Import/export of the {@code net_risk_band} scoring configuration. Each
 * {@code (net_risk_rtng, module)} group is assigned {@code MAX + 1} on upload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NetRiskBandConfigImportService implements OrlConfigImporter {

    static final String CONFIG_NAME = "net-risk-band";
    static final String[] EXPECTED_HEADERS = {"range_low", "range_high", "net_risk_rtng", "module"};
    private static final String[] DOWNLOAD_HEADERS = {
            "id", "config_version", "range_low", "range_high", "net_risk_rtng", "module", "CREATED_BY", "CREATE_DT_TM"
    };
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvImportProcessor csvImportProcessor;
    private final NetRiskBandCsvRowMapper rowMapper;
    private final NetRiskBandCsvRowValidator rowValidator;
    private final NetRiskBandRepository repository;
    private final ConfigVersionResolver configVersionResolver;

    @Override
    public String configName() {
        return CONFIG_NAME;
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
        log.debug("Importing {} config for user '{}'", CONFIG_NAME, username);

        List<NetRiskBandCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        Set<List<String>> batchKeys = rows.stream()
                .map(r -> List.of(r.getNetRiskRtng(), r.getModule()))
                .collect(Collectors.toSet());
        List<GroupMax> existing = repository.findGroupedMaxVersions().stream()
                .map(g -> new GroupMax(List.of(g.netRiskRtng(), g.module()), g.maxVersion()))
                .toList();
        Map<String, Integer> nextVersions = configVersionResolver.resolveNextVersions(existing, batchKeys);

        // CREATE_DT_TM is filled by the DB default on insert — not set here.
        List<NetRiskBand> entities = rows.stream()
                .map(r -> NetRiskBand.builder()
                        .configVersion(nextVersions.get(
                                ConfigVersionResolver.groupKey(r.getNetRiskRtng(), r.getModule())))
                        .rangeLow(r.getRangeLow())
                        .rangeHigh(r.getRangeHigh())
                        .netRiskRtng(NetRiskRating.fromDbValue(r.getNetRiskRtng()))
                        .module(Module.fromDbValue(r.getModule()))
                        .createdBy(username)
                        .build())
                .toList();

        repository.saveAll(entities);
        log.info("Imported {} {} row(s) by '{}'", entities.size(), CONFIG_NAME, username);
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
