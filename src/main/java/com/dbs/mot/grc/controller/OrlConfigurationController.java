package com.dbs.mot.grc.controller;

import com.dbs.mot.grc.csv.OrlConfigImporter;
import com.dbs.mot.grc.dto.ApiResponse;
import com.dbs.mot.grc.exception.UnauthorizedException;
import com.dbs.mot.grc.service.BuLocationHeadcountConfigImportService;
import com.dbs.mot.grc.service.BusinessUnitConfigImportService;
import com.dbs.mot.grc.service.EntityMasterConfigImportService;
import com.dbs.mot.grc.service.FeatureScoreBandConfigImportService;
import com.dbs.mot.grc.service.LandscapeDimensionConfigImportService;
import com.dbs.mot.grc.service.NetRiskBandConfigImportService;
import com.dbs.mot.grc.service.OrlConfigurationService;
import com.dbs.mot.grc.service.RiskTypeRiskAreaMapConfigImportService;
import com.dbs.mot.grc.service.TrainStatsConfigImportService;
import com.opencsv.CSVWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
 * <p>Each configuration has its own explicit, individually-documented upload and download
 * endpoint under {@code /api/orl-configurations}. The upload payload is CSV, but each table is an
 * ORL <em>configuration</em>; the per-table work lives in a {@code *ConfigImportService}
 * (implementing {@link OrlConfigImporter}) and the import+audit transaction in
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

    private static final String UPLOAD_HEADER = "X-EGRC-UserId";

    private final OrlConfigurationService configurationService;

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
            @RequestHeader(value = UPLOAD_HEADER, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(businessUnitService, username, file);
    }

    @Operation(summary = "Download business-unit configuration")
    @GetMapping("/biz-units/download")
    public void downloadBusinessUnits(HttpServletResponse response) throws IOException {
        download(businessUnitService, response);
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
            @RequestHeader(value = UPLOAD_HEADER, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(entityMasterService, username, file);
    }

    @Operation(summary = "Download entity-master configuration")
    @GetMapping("/entity-mstr/download")
    public void downloadEntityMaster(HttpServletResponse response) throws IOException {
        download(entityMasterService, response);
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
            @RequestHeader(value = UPLOAD_HEADER, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(buLocationHeadcountService, username, file);
    }

    @Operation(summary = "Download BU/location headcount configuration")
    @GetMapping("/bu-loctn-headcount/download")
    public void downloadBuLocationHeadcount(HttpServletResponse response) throws IOException {
        download(buLocationHeadcountService, response);
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
            @RequestHeader(value = UPLOAD_HEADER, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(riskTypeRiskAreaMapService, username, file);
    }

    @Operation(summary = "Download risk-type / risk-area map configuration")
    @GetMapping("/risk-type-risk-area-maps/download")
    public void downloadRiskTypeRiskAreaMaps(HttpServletResponse response) throws IOException {
        download(riskTypeRiskAreaMapService, response);
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
            @RequestHeader(value = UPLOAD_HEADER, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(featureScoreBandService, username, file);
    }

    @Operation(summary = "Download feature-score-band configuration (latest version per group)")
    @GetMapping("/feature-score-band/download")
    public void downloadFeatureScoreBand(HttpServletResponse response) throws IOException {
        download(featureScoreBandService, response);
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
            @RequestHeader(value = UPLOAD_HEADER, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(netRiskBandService, username, file);
    }

    @Operation(summary = "Download net-risk-band configuration (latest version per group)")
    @GetMapping("/net-risk-band/download")
    public void downloadNetRiskBand(HttpServletResponse response) throws IOException {
        download(netRiskBandService, response);
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
            @RequestHeader(value = UPLOAD_HEADER, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(trainStatsService, username, file);
    }

    @Operation(summary = "Download train-stats configuration (latest version per group)")
    @GetMapping("/train-stats/download")
    public void downloadTrainStats(HttpServletResponse response) throws IOException {
        download(trainStatsService, response);
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
            @RequestHeader(value = UPLOAD_HEADER, required = false) String username,
            @RequestParam("file") MultipartFile file) {
        return upload(landscapeDimensionService, username, file);
    }

    @Operation(summary = "Download landscape-dimension configuration")
    @GetMapping("/lndscp-dim/download")
    public void downloadLandscapeDimensions(HttpServletResponse response) throws IOException {
        download(landscapeDimensionService, response);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    /** Validates the operator header, runs the import+audit transaction, returns HTTP 201. */
    private ResponseEntity<ApiResponse<Void>> upload(OrlConfigImporter importer,
                                                     String username, MultipartFile file) {
        String user = requireUser(username);
        log.info("Upload of '{}' configuration requested by '{}'", importer.configName(), user);
        int count = configurationService.importConfig(importer, file, user, file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                count + " record(s) imported successfully into '" + importer.configName() + "'."));
    }

    /** Streams the configuration table as a CSV download. */
    private void download(OrlConfigImporter importer, HttpServletResponse response) throws IOException {
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

    /** Returns the trimmed operator id, or throws {@link UnauthorizedException} when missing/blank. */
    private String requireUser(String username) {
        if (username == null || username.isBlank()) {
            throw new UnauthorizedException("X-EGRC-UserId header is required and must not be blank.");
        }
        return username.trim();
    }
}
