package com.dbs.mot.grc.service;

import com.dbs.mot.grc.csv.CsvImportProcessor;
import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.dbs.mot.grc.csv.mapper.OrlLndscpDimCsvRowMapper;
import com.dbs.mot.grc.csv.validator.OrlLndscpDimCsvRowValidator;
import com.dbs.mot.grc.dto.OrlLndscpDimCsvRow;
import com.dbs.mot.grc.enums.DimStatus;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.repository.OrlLndscpDimRepository;
import com.dbs.mot.grc.util.RiskAreaParser;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.StreamSupport;

/**
 * Import/export of the {@code orl_lndscp_dim} landscape-dimension configuration.
 *
 * <p>{@code RISK_AREA} is a JSON array of risk-area groups (see {@link RiskAreaParser}) normalised
 * to compact JSON before storage. On re-upload the VERSION is advanced to one above the highest
 * VERSION stored for the uploaded CONFIG_IDs, avoiding unique-index conflicts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandscapeDimensionConfigImportService implements OrlConfigImporter {

    static final String CONFIG_NAME = "lndscp-dim";
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
    private final RiskAreaParser riskAreaParser;

    @Override
    public String configName() {
        return CONFIG_NAME;
    }

    @Override
    public String getDownloadFilename() {
        return "orl_lndscp_dim.csv";
    }

    @Override
    public String[] getDownloadHeaders() {
        return DOWNLOAD_HEADERS;
    }

    @Override
    @Transactional
    public int processAndImport(MultipartFile file, String username) {
        log.debug("Importing {} config for user '{}'", CONFIG_NAME, username);

        List<OrlLndscpDimCsvRow> rows = csvImportProcessor.process(
                file, EXPECTED_HEADERS, rowMapper, rowValidator);

        List<String> configIds = rows.stream()
                .map(OrlLndscpDimCsvRow::getConfigId)
                .toList();
        int version = resolveVersion(configIds);
        log.debug("Resolved VERSION={} for {} CONFIG_ID(s)", version, configIds.size());

        // CREATE_DT_TM is filled by the DB default on insert — not set here.
        List<OrlLndscpDim> entities = rows.stream()
                .map(r -> OrlLndscpDim.builder()
                        .configId(r.getConfigId())
                        .lndscpNm(r.getLndscpNm())
                        .effectStartDt(r.getEffectStartDt())
                        .effectEndDt(r.getEffectEndDt())
                        .version(version)
                        .status(DimStatus.ACTIVE)
                        .riskArea(riskAreaParser.normalizeCompact(r.getRiskArea()))
                        .bizUnits(r.getBizUnits())
                        .bizUnitLvl(r.getBizUnitLvl())
                        .locations(r.getLocations())
                        .createdBy(username)
                        .build())
                .toList();

        repository.saveAll(entities);
        log.info("Imported {} {} row(s) (version={}) by '{}'",
                entities.size(), CONFIG_NAME, version, username);
        return entities.size();
    }

    @Override
    public void writeDataRows(CSVWriter writer) {
        log.debug("Writing {} rows for download", CONFIG_NAME);
        StreamSupport.stream(repository.findAll().spliterator(), false).forEach(r ->
                writer.writeNext(new String[]{
                        s(r.getId()), r.getConfigId(), r.getLndscpNm(),
                        s(r.getEffectStartDt()), s(r.getEffectEndDt()),
                        s(r.getVersion()), r.getStatus().getDbValue(),
                        r.getRiskArea(), s(r.getBizUnits()), s(r.getBizUnitLvl()),
                        r.getLocations(), r.getCreatedBy(), fmt(r.getCreateDtTm())
                }));
    }

    /**
     * Resolves the VERSION to assign to this upload: one above the highest VERSION already stored
     * for any uploaded CONFIG_ID, or 1 when none exist yet.
     */
    int resolveVersion(List<String> configIds) {
        if (configIds == null || configIds.isEmpty()) {
            return 1;
        }
        int maxVersion = repository.findMaxVersionByConfigIds(configIds);
        int resolved = maxVersion > 0 ? maxVersion + 1 : 1;
        log.debug("Version resolution over {} config id(s): maxExisting={} resolved={}",
                configIds.size(), maxVersion, resolved);
        return resolved;
    }

    private String s(Object v) {
        return v == null ? "" : v.toString();
    }

    private String fmt(LocalDateTime dt) {
        return dt == null ? "" : dt.format(DT_FMT);
    }
}
