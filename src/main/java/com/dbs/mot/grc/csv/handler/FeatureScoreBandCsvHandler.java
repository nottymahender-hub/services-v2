package com.dbs.mot.grc.csv.handler;

import com.dbs.mot.grc.common.csv.CsvHandler;
import com.dbs.mot.grc.common.csv.CsvImportProcessor;
import com.dbs.mot.grc.common.util.ConfigVersionResolver;
import com.dbs.mot.grc.csv.mapper.FeatureScoreBandCsvRowMapper;
import com.dbs.mot.grc.csv.validator.FeatureScoreBandCsvRowValidator;
import com.dbs.mot.grc.dto.FeatureScoreBandCsvRow;
import com.dbs.mot.grc.entity.FeatureScoreBand;
import com.dbs.mot.grc.repository.FeatureScoreBandRepository;
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
 * Handles CSV upload/download for {@code feature_score_band}.
 * Registered as {@code "feature-score-band"}.
 *
 * <p>On re-upload, {@link ConfigVersionResolver} detects the conflict and
 * automatically advances {@code config_version} to {@code MAX + 1}.
 *
 * <p>Download returns only the <em>latest</em> {@code config_version} row per
 * {@code (feature_name, feature_bin, module)} group — older versions are fetched (a
 * single plain {@code findAll()}, no SQL grouping) but filtered out here in Java,
 * keeping the query itself unchanged and the "latest wins" rule in one place.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureScoreBandCsvHandler implements CsvHandler {

    static final String TABLE_NAME = "feature-score-band";
    static final String DB_TABLE = "feature_score_band";
    static final String[] EXPECTED_HEADERS = {
            "feature_name", "feature_bin", "range_low", "range_high", "score", "module"
    };
    private static final String[] DOWNLOAD_HEADERS = {
            "id", "config_version", "feature_bin", "feature_name",
            "range_low", "range_high", "score", "module", "CREATED_BY", "CREATE_DT_TM"
    };
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> GROUP_COLS = List.of("feature_name", "feature_bin", "module");

    private final CsvImportProcessor csvImportProcessor;
    private final FeatureScoreBandCsvRowMapper rowMapper;
    private final FeatureScoreBandCsvRowValidator rowValidator;
    private final FeatureScoreBandRepository repository;
    private final ConfigVersionResolver configVersionResolver;
    private final JdbcAggregateOperations jdbcAggregateOperations;

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public String getDownloadFilename() {
        return "feature_score_band.csv";
    }

    @Override
    public String[] getDownloadHeaders() {
        return DOWNLOAD_HEADERS;
    }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        List<FeatureScoreBandCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        Set<List<String>> groupKeys = rows.stream()
                .map(r -> List.of(r.getFeatureName(), String.valueOf(r.getFeatureBin()), r.getModule()))
                .collect(Collectors.toSet());
        Map<String, Integer> nextVersions =
                configVersionResolver.resolveNextVersions(DB_TABLE, GROUP_COLS, groupKeys);

        LocalDateTime now = LocalDateTime.now();
        List<FeatureScoreBand> entities = rows.stream()
                .map(r -> {
                    String key = ConfigVersionResolver.groupKey(
                            r.getFeatureName(), String.valueOf(r.getFeatureBin()), r.getModule());
                    return FeatureScoreBand.builder()
                            .configVersion(nextVersions.get(key))
                            .featureBin(r.getFeatureBin()).featureName(r.getFeatureName())
                            .rangeLow(r.getRangeLow()).rangeHigh(r.getRangeHigh())
                            .score(r.getScore()).module(r.getModule())
                            .createdBy(username).createDtTm(now).build();
                })
                .collect(Collectors.toList());

        jdbcAggregateOperations.insertAll(entities);
        log.info("Imported {} feature_score_band rows by '{}'", entities.size(), username);
        return entities.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        latestPerGroup().forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getId()), s(r.getConfigVersion()), s(r.getFeatureBin()),
                        r.getFeatureName(), s(r.getRangeLow()), s(r.getRangeHigh()),
                        s(r.getScore()), r.getModule(), r.getCreatedBy(), fmt(r.getCreateDtTm())
                }));
    }

    /**
     * Reduces all rows to the highest {@code config_version} per
     * {@code (feature_name, feature_bin, module)} group.
     */
    private Collection<FeatureScoreBand> latestPerGroup() {
        Map<String, FeatureScoreBand> latestByGroup = new LinkedHashMap<>();
        for (FeatureScoreBand row : repository.findAll()) {
            String key = ConfigVersionResolver.groupKey(
                    row.getFeatureName(), String.valueOf(row.getFeatureBin()), row.getModule());
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
