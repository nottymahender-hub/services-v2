package com.dbs.mot.grc.csv.handler;

import com.dbs.mot.grc.common.csv.CsvHandler;
import com.dbs.mot.grc.common.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.mapper.BuLctnHeadcountCsvRowMapper;
import com.dbs.mot.grc.csv.validator.BuLctnHeadcountCsvRowValidator;
import com.dbs.mot.grc.dto.BuLctnHeadcountCsvRow;
import com.dbs.mot.grc.repository.OrlBuLctnHeadcountRepository;
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
 * Handles CSV upload/download for {@code orl_bu_loctn_headcount}.
 * Registered as {@code "bu-loctn-headcount"}.
 *
 * <p>Upload uses a single batched INSERT … ON DUPLICATE KEY UPDATE, so re-uploading a
 * row updates the headcount in place (upsert). The duplicate key is the unique index on
 * (ORL_BU_NM_L2, ORL_BU_NM_L3, ORL_BU_NM_L4, location).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuLctnHeadcountCsvHandler implements CsvHandler {

    static final String TABLE_NAME = "bu-loctn-headcount";
    static final String[] EXPECTED_HEADERS = {
            "ORL_BU_NM_L2", "ORL_BU_NM_L3", "ORL_BU_NM_L4", "location", "headcount"
    };
    private static final String[] DOWNLOAD_HEADERS = {
            "id", "ORL_BU_NM_L2", "ORL_BU_NM_L3", "ORL_BU_NM_L4",
            "location", "headcount", "uploadedBy", "uploadedAt"
    };
    private static final String UPSERT_SQL = """
            INSERT INTO orl_bu_loctn_headcount
                (ORL_BU_NM_L2, ORL_BU_NM_L3, ORL_BU_NM_L4, location, headcount,
                 CREATED_BY, CREATE_DT_TM)
            VALUES (:orlBuNmL2, :orlBuNmL3, :orlBuNmL4, :location, :headcount, :createdBy, NOW())
            ON DUPLICATE KEY UPDATE
                headcount    = VALUES(headcount),
                UPDATED_BY   = VALUES(CREATED_BY),
                UPDATE_DT_TM = NOW()
            """;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvImportProcessor csvImportProcessor;
    private final BuLctnHeadcountCsvRowMapper rowMapper;
    private final BuLctnHeadcountCsvRowValidator rowValidator;
    private final OrlBuLctnHeadcountRepository repository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public String getTableName()         { return TABLE_NAME; }
    @Override
    public String getDownloadFilename()  { return "orl_bu_loctn_headcount.csv"; }
    @Override
    public String[] getDownloadHeaders() { return DOWNLOAD_HEADERS; }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        log.debug("Processing orl_bu_loctn_headcount CSV upload for user '{}'", username);

        List<BuLctnHeadcountCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        SqlParameterSource[] batchArgs = rows.stream()
                .map(r -> (SqlParameterSource) new MapSqlParameterSource()
                        .addValue("orlBuNmL2", r.getOrlBuNmL2())
                        .addValue("orlBuNmL3", r.getOrlBuNmL3())
                        .addValue("orlBuNmL4", r.getOrlBuNmL4())
                        .addValue("location", r.getLocation())
                        .addValue("headcount", r.getHeadcount())
                        .addValue("createdBy", username))
                .toArray(SqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(UPSERT_SQL, batchArgs);
        log.info("Upserted {} orl_bu_loctn_headcount row(s) by '{}'", rows.size(), username);
        return rows.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        log.debug("Writing orl_bu_loctn_headcount rows for download");
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getId()), r.getOrlBuNmL2(), s(r.getOrlBuNmL3()), s(r.getOrlBuNmL4()),
                        r.getLocation(), s(r.getHeadcount()),
                        StringUtils.isBlank(r.getUpdatedBy()) ? r.getCreatedBy() : r.getUpdatedBy(),
                        fmt(Objects.isNull(r.getUpdateDtTm()) ? r.getCreateDtTm() : r.getUpdateDtTm())
                }));
    }

    private String s(Object v) { return v == null ? "" : v.toString(); }

    private String fmt(LocalDateTime dt) { return dt == null ? "" : dt.format(DT_FMT); }
}
