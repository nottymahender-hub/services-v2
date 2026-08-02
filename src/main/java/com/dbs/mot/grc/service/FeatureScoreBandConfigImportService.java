package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.dbs.mot.grc.csv.mapper.FeatureScoreBandCsvRowMapper;
import com.dbs.mot.grc.csv.validator.FeatureScoreBandCsvRowValidator;
import com.dbs.mot.grc.dto.FeatureScoreBandCsvRow;
import com.dbs.mot.grc.enums.Module;
import com.dbs.mot.grc.entity.FeatureScoreBand;
import com.dbs.mot.grc.repository.FeatureScoreBandRepository;
import com.dbs.mot.grc.util.ConfigVersionResolver;
import com.dbs.mot.grc.util.ConfigVersionResolver.GroupMax;
import com.dbs.mot.grc.util.CsvFormatters;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Import/export of the {@code feature_score_band} scoring configuration.
 *
 * <p>On upload each natural-key group {@code (feature_name, feature_bin, module)} is assigned the
 * next {@code config_version} ({@code MAX + 1}) computed by {@link ConfigVersionResolver} from the
 * current per-group maxima read through the repository; rows are then inserted. Download returns
 * only the latest version per group (reduced in memory).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureScoreBandConfigImportService implements OrlConfigImporter {

    static final String CONFIG_NAME = "feature-score-band";
    static final String[] EXPECTED_HEADERS = {
            "feature_name", "feature_bin", "range_low", "range_high", "score", "module"
    };
    private static final String[] DOWNLOAD_HEADERS = {
            "id", "config_version", "feature_bin", "feature_name",
            "range_low", "range_high", "score", "module", "CREATED_BY", "CREATE_DT_TM"
    };

    private final CsvImportProcessor csvImportProcessor;
    private final FeatureScoreBandCsvRowMapper rowMapper;
    private final FeatureScoreBandCsvRowValidator rowValidator;
    private final FeatureScoreBandRepository repository;
    private final ConfigVersionResolver configVersionResolver;

    @Override
    public String configName() {
        return CONFIG_NAME;
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
        log.debug("Importing {} config for user '{}'", CONFIG_NAME, username);

        List<FeatureScoreBandCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        Set<List<String>> batchKeys = rows.stream()
                .map(r -> groupKey(r.getFeatureName(), r.getFeatureBin(), r.getModule()))
                .collect(Collectors.toSet());
        List<GroupMax> existing = repository.findGroupedMaxVersions().stream()
                .map(g -> new GroupMax(
                        List.of(g.featureName(), String.valueOf(g.featureBin()), g.module()),
                        g.maxVersion()))
                .toList();
        Map<String, Integer> nextVersions = configVersionResolver.resolveNextVersions(existing, batchKeys);

        // CREATE_DT_TM is filled by the DB default on insert — not set here.
        List<FeatureScoreBand> entities = rows.stream()
                .map(r -> FeatureScoreBand.builder()
                        .configVersion(nextVersions.get(ConfigVersionResolver.groupKey(
                                r.getFeatureName(), String.valueOf(r.getFeatureBin()), r.getModule())))
                        .featureBin(r.getFeatureBin())
                        .featureName(r.getFeatureName())
                        .rangeLow(r.getRangeLow())
                        .rangeHigh(r.getRangeHigh())
                        .score(r.getScore())
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
                        CsvFormatters.cell(r.getId()), CsvFormatters.cell(r.getConfigVersion()), CsvFormatters.cell(r.getFeatureBin()),
                        r.getFeatureName(), CsvFormatters.cell(r.getRangeLow()), CsvFormatters.cell(r.getRangeHigh()),
                        CsvFormatters.cell(r.getScore()), r.getModule().getDbValue(), r.getCreatedBy(), CsvFormatters.cell(r.getCreateDtTm())
                }));
    }

    /** Reduces all stored rows to the highest {@code config_version} per natural-key group. */
    private java.util.Collection<FeatureScoreBand> latestPerGroup() {
        Map<String, FeatureScoreBand> latestByGroup = new LinkedHashMap<>();
        for (FeatureScoreBand row : repository.findAll()) {
            String key = ConfigVersionResolver.groupKey(
                    row.getFeatureName(), String.valueOf(row.getFeatureBin()), row.getModule().getDbValue());
            latestByGroup.merge(key, row,
                    (existing, candidate) -> candidate.getConfigVersion() > existing.getConfigVersion()
                            ? candidate : existing);
        }
        return latestByGroup.values();
    }

    private List<String> groupKey(String featureName, int featureBin, String module) {
        return List.of(featureName, String.valueOf(featureBin), module);
    }
}
