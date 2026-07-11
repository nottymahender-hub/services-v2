package com.dbs.mot.grc.csv.handler;

import com.dbs.mot.grc.common.csv.CsvHandler;
import com.dbs.mot.grc.common.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.mapper.BizUnitCsvRowMapper;
import com.dbs.mot.grc.csv.validator.BizUnitCsvRowValidator;
import com.dbs.mot.grc.dto.BizUnitCsvRow;
import com.dbs.mot.grc.repository.OrlBizUnitRepository;
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
 * Handles CSV upload/download for {@code orl_biz_unit}.
 * Registered as table name {@code "biz-units"}.
 * Uses INSERT … ON DUPLICATE KEY UPDATE so the same file can be re-uploaded (upsert).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BizUnitCsvHandler implements CsvHandler {

    static final String TABLE_NAME = "biz-units";
    static final String[] EXPECTED_HEADERS = {
            "BU_NUM", "BU_NM", "LVL_OF_HIER", "ORL_BU_NM_L2", "ORL_BU_NM_L3", "ORL_BU_NM_L4", "BU_FULL_PATH"
    };
    private static final String[] DOWNLOAD_HEADERS = {
            "BU_NUM", "BU_NM", "LVL_OF_HIER", "ORL_BU_NM_L2", "ORL_BU_NM_L3",
            "ORL_BU_NM_L4", "BU_FULL_PATH", "uploadedBy", "uploadedAt"
    };
    private static final String UPSERT_SQL = """
            INSERT INTO orl_biz_unit
                (BU_NUM, BU_NM, LVL_OF_HIER, ORL_BU_NM_L2, ORL_BU_NM_L3,
                 ORL_BU_NM_L4, BU_FULL_PATH, CREATED_BY, CREATE_DT_TM)
            VALUES (:buNum, :buNm, :lvlOfHier, :orlBuNmL2, :orlBuNmL3,
                    :orlBuNmL4, :buFullPath, :createdBy, NOW())
            ON DUPLICATE KEY UPDATE
                BU_NM        = VALUES(BU_NM),
                LVL_OF_HIER  = VALUES(LVL_OF_HIER),
                ORL_BU_NM_L2 = VALUES(ORL_BU_NM_L2),
                ORL_BU_NM_L3 = VALUES(ORL_BU_NM_L3),
                ORL_BU_NM_L4 = VALUES(ORL_BU_NM_L4),
                BU_FULL_PATH = VALUES(BU_FULL_PATH),
                UPDATED_BY   = VALUES(CREATED_BY),
                UPDATE_DT_TM = NOW()
            """;
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CsvImportProcessor csvImportProcessor;
    private final BizUnitCsvRowMapper rowMapper;
    private final BizUnitCsvRowValidator rowValidator;
    private final OrlBizUnitRepository repository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public String getDownloadFilename() {
        return "orl_biz_unit.csv";
    }

    @Override
    public String[] getDownloadHeaders() {
        return DOWNLOAD_HEADERS;
    }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        List<BizUnitCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        SqlParameterSource[] batchArgs = rows.stream()
                .map(r -> (SqlParameterSource) new MapSqlParameterSource()
                        .addValue("buNum", r.getBuNum())
                        .addValue("buNm", r.getBuNm())
                        .addValue("lvlOfHier", r.getLvlOfHier())
                        .addValue("orlBuNmL2", r.getOrlBuNmL2())
                        .addValue("orlBuNmL3", r.getOrlBuNmL3())
                        .addValue("orlBuNmL4", r.getOrlBuNmL4())
                        .addValue("buFullPath", r.getBuFullPath())
                        .addValue("createdBy", username))
                .toArray(SqlParameterSource[]::new);

        namedParameterJdbcTemplate.batchUpdate(UPSERT_SQL, batchArgs);
        log.info("Upserted {} orl_biz_unit row(s) by '{}'", rows.size(), username);
        return rows.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getBuNum()), r.getBuNm(), s(r.getLvlOfHier()),
                        r.getOrlBuNmL2(), r.getOrlBuNmL3(), r.getOrlBuNmL4(),
                        r.getBuFullPath(), StringUtils.isBlank(r.getUpdatedBy()) ? r.getCreatedBy() : r.getUpdatedBy(),
                        fmt(Objects.isNull(r.getUpdateDtTm()) ? r.getCreateDtTm() : r.getUpdateDtTm())
                }));
    }

    private String s(Object v) {
        return v == null ? "" : v.toString();
    }

    private String fmt(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DT_FMT);
    }
}
