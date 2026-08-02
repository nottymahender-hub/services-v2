package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.dbs.mot.grc.csv.mapper.TrainStatsCsvRowMapper;
import com.dbs.mot.grc.csv.validator.TrainStatsCsvRowValidator;
import com.dbs.mot.grc.dto.TrainStatsCsvRow;
import com.dbs.mot.grc.enums.LevelCategory;
import com.dbs.mot.grc.enums.Module;
import com.dbs.mot.grc.entity.TrainStats;
import com.dbs.mot.grc.repository.TrainStatsRepository;
import com.dbs.mot.grc.util.ConfigVersionResolver;
import com.dbs.mot.grc.util.ConfigVersionResolver.GroupMax;
import com.dbs.mot.grc.util.CsvFormatters;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Import/export of the {@code train_stats} scoring configuration. Same versioning rule as the
 * other scoring tables: each {@code (lvl, module)} group is assigned {@code MAX + 1} on upload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainStatsConfigImportService implements OrlConfigImporter {

    static final String CONFIG_NAME = "train-stats";
    static final String[] EXPECTED_HEADERS = {"lvl", "train_mean", "train_var", "module"};
    private static final String[] DOWNLOAD_HEADERS = {
            "id", "config_version", "lvl", "train_mean", "train_var", "module", "CREATED_BY", "CREATE_DT_TM"
    };

    private final CsvImportProcessor csvImportProcessor;
    private final TrainStatsCsvRowMapper rowMapper;
    private final TrainStatsCsvRowValidator rowValidator;
    private final TrainStatsRepository repository;
    private final ConfigVersionResolver configVersionResolver;

    @Override
    public String configName() {
        return CONFIG_NAME;
    }

    @Override
    public String getDownloadFilename() {
        return "train_stats.csv";
    }

    @Override
    public String[] getDownloadHeaders() {
        return DOWNLOAD_HEADERS;
    }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        log.debug("Importing {} config for user '{}'", CONFIG_NAME, username);

        List<TrainStatsCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        Set<List<String>> batchKeys = rows.stream()
                .map(r -> List.of(r.getLvl(), r.getModule()))
                .collect(Collectors.toSet());
        List<GroupMax> existing = repository.findGroupedMaxVersions().stream()
                .map(g -> new GroupMax(List.of(g.lvl(), g.module()), g.maxVersion()))
                .toList();
        Map<String, Integer> nextVersions = configVersionResolver.resolveNextVersions(existing, batchKeys);

        // CREATE_DT_TM is filled by the DB default on insert — not set here.
        List<TrainStats> entities = rows.stream()
                .map(r -> TrainStats.builder()
                        .configVersion(nextVersions.get(ConfigVersionResolver.groupKey(r.getLvl(), r.getModule())))
                        .lvl(LevelCategory.fromDbValue(r.getLvl()))
                        .trainMean(r.getTrainMean())
                        .trainVar(r.getTrainVar())
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
                        CsvFormatters.cell(r.getId()), CsvFormatters.cell(r.getConfigVersion()), r.getLvl().getDbValue(),
                        CsvFormatters.cell(r.getTrainMean()), CsvFormatters.cell(r.getTrainVar()),
                        r.getModule().getDbValue(), r.getCreatedBy(), CsvFormatters.cell(r.getCreateDtTm())
                }));
    }

    /** Reduces all rows to the highest {@code config_version} per {@code (lvl, module)} group. */
    private Collection<TrainStats> latestPerGroup() {
        Map<String, TrainStats> latestByGroup = new LinkedHashMap<>();
        for (TrainStats row : repository.findAll()) {
            String key = ConfigVersionResolver.groupKey(row.getLvl().getDbValue(), row.getModule().getDbValue());
            latestByGroup.merge(key, row,
                    (existing, candidate) -> candidate.getConfigVersion() > existing.getConfigVersion()
                            ? candidate : existing);
        }
        return latestByGroup.values();
    }
}
