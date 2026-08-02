package com.dbs.mot.grc.controller;

import com.dbs.mot.grc.csv.ConfigExporter;
import com.dbs.mot.grc.csv.ConfigUploader;
import com.dbs.mot.grc.csv.OrlConfigImporterRegistry;
import com.dbs.mot.grc.dto.ApiResponse;
import com.dbs.mot.grc.service.BuLocationHeadcountConfigImportService;
import com.dbs.mot.grc.service.BusinessUnitConfigImportService;
import com.dbs.mot.grc.service.EntityMasterConfigImportService;
import com.dbs.mot.grc.service.FeatureScoreBandConfigImportService;
import com.dbs.mot.grc.service.LandscapeDimensionConfigImportService;
import com.dbs.mot.grc.service.NetRiskBandConfigImportService;
import com.dbs.mot.grc.service.OrlConfigurationService;
import com.dbs.mot.grc.service.RiskTypeRiskAreaMapConfigImportService;
import com.dbs.mot.grc.service.TrainStatsConfigImportService;
import com.dbs.mot.grc.util.OperatorHeader;
import com.opencsv.CSVWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * REST endpoints for uploading and downloading the ORL configuration tables.
 *
 * <p>Each configuration has its own explicit, individually-documented <em>upload</em> endpoint
 * under {@code /api/orl-configurations}. Downloads, by contrast, are served by a <em>single</em>
 * endpoint ({@code GET /{configName}/download}) that resolves the target table through
 * {@link OrlConfigImporterRegistry}. The upload payload is CSV, but each table is an ORL
 * <em>configuration</em>; the per-table work lives in a {@code *ConfigImportService}
 * (implementing {@link com.dbs.mot.grc.csv.OrlConfigImporter}) and the import+audit transaction in
 * {@link OrlConfigurationService}.
 *
 * <p><b>Authentication:</b> uploads require the {@code X-EGRC-UserId} header (stored as
 * {@code CREATED_BY}); missing/blank → HTTP 401. Downloads are unauthenticated.
 */
@Slf4j
@RestController
@RequestMapping("/api/orl-configurations")
@RequiredArgsConstructor
@Tag(name = "ORL Configurations", description = "Upload and download of the ORL configuration tables.")
public class OrlConfigurationController {

    private final OrlConfigurationService configurationService;
    /** Resolves a config name to its importer for the single download endpoint. */
    private final OrlConfigImporterRegistry importerRegistry;

    private final BusinessUnitConfigImportService businessUnitService;
    private final EntityMasterConfigImportService entityMasterService;
    private final BuLocationHeadcountConfigImportService buLocationHeadcountService;
    private final RiskTypeRiskAreaMapConfigImportService riskTypeRiskAreaMapService;
    private final FeatureScoreBandConfigImportService featureScoreBandService;
    private final NetRiskBandConfigImportService netRiskBandService;
    private final TrainStatsConfigImportService trainStatsService;
    private final LandscapeDimensionConfigImportService landscapeDimensionService;

    // ── Business units ───────────────────────────────────────────────────────

    @Operation(summary = "Upload business-unit configuration",
            description = "Imports orl_biz_unit rows (upsert on BU_NUM).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Rows imported"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CSV validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping(value = "/biz-units/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadBusinessUnits(
            @RequestHeader(value = OperatorHeader.NAME, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(businessUnitService, username, file);
    }

    // ── Entity master ────────────────────────────────────────────────────────

    @Operation(summary = "Upload entity-master configuration",
            description = "Imports orl_entity_mstr rows (upsert on ENTITY_NUM).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Rows imported"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CSV validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping(value = "/entity-mstr/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadEntityMaster(
            @RequestHeader(value = OperatorHeader.NAME, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(entityMasterService, username, file);
    }

    // ── BU/location headcount ────────────────────────────────────────────────

    @Operation(summary = "Upload BU/location headcount configuration",
            description = "Imports orl_bu_loctn_headcount rows (upsert on the BU/location composite key).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Rows imported"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CSV validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping(value = "/bu-loctn-headcount/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadBuLocationHeadcount(
            @RequestHeader(value = OperatorHeader.NAME, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(buLocationHeadcountService, username, file);
    }

    // ── Risk-type / risk-area map ─────────────────────────────────────────────

    @Operation(summary = "Upload risk-type / risk-area map configuration",
            description = "Imports orl_risk_type_risk_area_map rows (append-only).")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Rows imported"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CSV validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping(value = "/risk-type-risk-area-maps/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadRiskTypeRiskAreaMaps(
            @RequestHeader(value = OperatorHeader.NAME, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(riskTypeRiskAreaMapService, username, file);
    }

    // ── Feature score band ────────────────────────────────────────────────────

    @Operation(summary = "Upload feature-score-band configuration",
            description = "Imports feature_score_band rows; assigns the next config_version per group.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Rows imported"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CSV validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping(value = "/feature-score-band/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadFeatureScoreBand(
            @RequestHeader(value = OperatorHeader.NAME, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(featureScoreBandService, username, file);
    }

    // ── Net risk band ─────────────────────────────────────────────────────────

    @Operation(summary = "Upload net-risk-band configuration",
            description = "Imports net_risk_band rows; assigns the next config_version per group.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Rows imported"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CSV validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping(value = "/net-risk-band/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadNetRiskBand(
            @RequestHeader(value = OperatorHeader.NAME, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(netRiskBandService, username, file);
    }

    // ── Train stats ───────────────────────────────────────────────────────────

    @Operation(summary = "Upload train-stats configuration",
            description = "Imports train_stats rows; assigns the next config_version per group.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Rows imported"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CSV validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping(value = "/train-stats/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadTrainStats(
            @RequestHeader(value = OperatorHeader.NAME, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(trainStatsService, username, file);
    }

    // ── Landscape dimensions ──────────────────────────────────────────────────

    @Operation(summary = "Upload landscape-dimension configuration",
            description = "Imports orl_lndscp_dim rows; auto-advances VERSION on re-upload.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Rows imported"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "CSV validation failure"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping(value = "/lndscp-dim/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> uploadLandscapeDimensions(
            @RequestHeader(value = OperatorHeader.NAME, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(landscapeDimensionService, username, file);
    }

    // ── Single CSV download (any configuration) ────────────────────────────────

    @Operation(summary = "Download a configuration table as CSV",
            description = "Streams the requested ORL configuration table as a CSV file. The table is "
                    + "selected by the {configName} path segment; valid values are: biz-units, "
                    + "entity-mstr, bu-loctn-headcount, risk-type-risk-area-maps, feature-score-band, "
                    + "net-risk-band, train-stats, lndscp-dim.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "CSV stream returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unknown configName"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{configName}/download")
    public void downloadConfiguration(
            @Parameter(description = "Configuration table identifier, e.g. 'biz-units'", required = true)
            @PathVariable String configName,
            HttpServletResponse response) throws IOException {
        // Unknown configName → NotFoundException (404) from the registry, before any streaming.
        download(importerRegistry.get(configName), response);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /** Validates the operator header, runs the import+audit transaction, returns HTTP 201. */
    private ResponseEntity<ApiResponse<Void>> upload(ConfigUploader importer,
                                                     String username, MultipartFile file) {
        String user = OperatorHeader.require(username);
        log.info("Upload of '{}' configuration requested by '{}'", importer.configName(), user);
        int count = configurationService.importConfig(importer, file, user, file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                count + " record(s) imported successfully into '" + importer.configName() + "'."));
    }

    /** Streams the configuration table as a CSV download. */
    private void download(ConfigExporter importer, HttpServletResponse response) throws IOException {
        log.info("Download of '{}' configuration requested", importer.configName());
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + importer.getDownloadFilename() + "\"");
        try (CSVWriter writer = new CSVWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.writeNext(importer.getDownloadHeaders());
            importer.writeDataRows(writer);
        }
    }
}
