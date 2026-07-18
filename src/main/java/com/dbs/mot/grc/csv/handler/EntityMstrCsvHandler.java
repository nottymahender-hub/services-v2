package com.dbs.mot.grc.csv.handler;

import com.dbs.mot.grc.common.csv.CsvHandler;
import com.dbs.mot.grc.common.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.mapper.EntityMstrCsvRowMapper;
import com.dbs.mot.grc.csv.validator.EntityMstrCsvRowValidator;
import com.dbs.mot.grc.dto.EntityMstrCsvRow;
import com.dbs.mot.grc.repository.OrlEntityMstrRepository;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

/**
 * Handles CSV upload/download for {@code orl_entity_mstr}.
 * Registered as {@code "entity-mstr"}.
 * Upload uses a single batched INSERT … ON DUPLICATE KEY UPDATE (upsert).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntityMstrCsvHandler implements CsvHandler {

    static final String TABLE_NAME = "entity-mstr";
    static final String[] EXPECTED_HEADERS = {"ENTITY_NUM", "ENTITY_NM", "orl_location", "orl_location_ic"};
    private static final String[] DOWNLOAD_HEADERS = {
            "ENTITY_NUM", "ENTITY_NM", "orl_location", "orl_location_ic", "uploadedBy", "uploadedAt"
    };
    private static final String UPSERT_SQL = """
            INSERT INTO orl_entity_mstr
                (ENTITY_NUM, ENTITY_NM, orl_location, orl_location_ic, CREATED_BY, CREATED_DT_TM)
            VALUES (:entityNum, :entityNm, :orlLocation, :orlLocationIc, :createdBy, NOW())
            ON DUPLICATE KEY UPDATE
                ENTITY_NM       = VALUES(ENTITY_NM),
                orl_location    = VALUES(orl_location),
                orl_location_ic = VALUES(orl_location_ic),
                UPDATED_BY      = VALUES(CREATED_BY),
                UPDATE_DT_TM    = NOW()
            """;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvImportProcessor csvImportProcessor;
    private final EntityMstrCsvRowMapper rowMapper;
    private final EntityMstrCsvRowValidator rowValidator;
    private final OrlEntityMstrRepository repository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public String getTableName() { return TABLE_NAME; }

    @Override
    public String getDownloadFilename() { return "orl_entity_mstr.csv"; }

    @Override
    public String[] getDownloadHeaders() { return DOWNLOAD_HEADERS; }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        List<EntityMstrCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        SqlParameterSource[] batchArgs = rows.stream()
                .map(r -> (SqlParameterSource) new MapSqlParameterSource()
                        .addValue("entityNum", r.getEntityNum())
                        .addValue("entityNm", r.getEntityNm())
                        .addValue("orlLocation", r.getOrlLocation() == null ? "" : r.getOrlLocation())
                        .addValue("orlLocationIc", r.getOrlLocationIc())
                        .addValue("createdBy", username))
                .toArray(SqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(UPSERT_SQL, batchArgs);
        log.info("Upserted {} orl_entity_mstr row(s) by '{}'", rows.size(), username);
        return rows.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getEntityNum()), r.getEntityNm(),
                        r.getOrlLocation(), r.getOrlLocationIc(),
                        StringUtils.isBlank(r.getUpdatedBy()) ? r.getCreatedBy() : r.getUpdatedBy(),
                        fmt(Objects.isNull(r.getUpdateDtTm()) ? r.getCreatedDtTm() : r.getUpdateDtTm())
                }));
    }

    private String s(Object v) { return v == null ? "" : v.toString(); }

    private String fmt(LocalDateTime dt) { return dt == null ? "" : dt.format(DT_FMT); }
}
