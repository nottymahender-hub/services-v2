package com.dbs.mot.grc.csv.handler;

import com.dbs.mot.grc.common.csv.CsvHandler;
import com.dbs.mot.grc.common.csv.CsvImportProcessor;
import com.dbs.mot.grc.common.util.ConfigVersionResolver;
import com.dbs.mot.grc.csv.mapper.TrainStatsCsvRowMapper;
import com.dbs.mot.grc.csv.validator.TrainStatsCsvRowValidator;
import com.dbs.mot.grc.dto.TrainStatsCsvRow;
import com.dbs.mot.grc.entity.TrainStats;
import com.dbs.mot.grc.repository.TrainStatsRepository;
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
 * Handles CSV upload/download for {@code train_stats}. Registered as {@code "train-stats"}.
 *
 * <p>Download returns only the <em>latest</em> {@code config_version} row per
 * {@code (lvl, module)} group — filtered in Java after a plain {@code findAll()},
 * keeping the query itself unchanged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainStatsCsvHandler implements CsvHandler {

    static final String TABLE_NAME = "train-stats";
    static final String DB_TABLE = "train_stats";
    static final String[] EXPECTED_HEADERS = {"lvl", "train_mean", "train_var", "module"};
    private static final List<String> GROUP_COLS = List.of("lvl", "module");
    private static final String[] DOWNLOAD_HEADERS = {
            "id", "config_version", "lvl", "train_mean", "train_var", "module", "CREATED_BY", "CREATE_DT_TM"
    };
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvImportProcessor csvImportProcessor;
    private final TrainStatsCsvRowMapper rowMapper;
    private final TrainStatsCsvRowValidator rowValidator;
    private final TrainStatsRepository repository;
    private final ConfigVersionResolver configVersionResolver;
    private final JdbcAggregateOperations jdbcAggregateOperations;

    @Override
    public String getTableName() {
        return TABLE_NAME;
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
        List<TrainStatsCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        Set<List<String>> groupKeys = rows.stream()
                .map(r -> List.of(r.getLvl(), r.getModule()))
                .collect(Collectors.toSet());
        Map<String, Integer> nextVersions =
                configVersionResolver.resolveNextVersions(DB_TABLE, GROUP_COLS, groupKeys);

        LocalDateTime now = LocalDateTime.now();
        List<TrainStats> entities = rows.stream()
                .map(r -> {
                    String key = ConfigVersionResolver.groupKey(r.getLvl(), r.getModule());
                    return TrainStats.builder()
                            .configVersion(nextVersions.get(key)).lvl(r.getLvl())
                            .trainMean(r.getTrainMean()).trainVar(r.getTrainVar())
                            .module(r.getModule()).createdBy(username).createDtTm(now).build();
                })
                .toList();

        jdbcAggregateOperations.insertAll(entities);
        log.info("Imported {} train_stats rows by '{}'", entities.size(), username);
        return entities.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        latestPerGroup().forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getId()), s(r.getConfigVersion()), r.getLvl(),
                        s(r.getTrainMean()), s(r.getTrainVar()),
                        r.getModule(), r.getCreatedBy(), fmt(r.getCreateDtTm())
                }));
    }

    /** Reduces all rows to the highest {@code config_version} per {@code (lvl, module)} group. */
    private Collection<TrainStats> latestPerGroup() {
        Map<String, TrainStats> latestByGroup = new LinkedHashMap<>();
        for (TrainStats row : repository.findAll()) {
            String key = ConfigVersionResolver.groupKey(row.getLvl(), row.getModule());
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
