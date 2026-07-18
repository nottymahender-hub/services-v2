package com.dbs.mot.grc.controller;

import com.dbs.mot.grc.common.dto.ApiResponse;
import com.dbs.mot.grc.dto.AssmtDetailResponse;
import com.dbs.mot.grc.dto.BulkAssmtGenerationResponse;
import com.dbs.mot.grc.dto.LandscapeAssmtDetailSummary;
import com.dbs.mot.grc.dto.LandscapeDimensions;
import com.dbs.mot.grc.service.BulkAssmtGenerationService;
import com.dbs.mot.grc.service.LandscapeAssmtDetailsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for landscape assessments.
 *
 * <pre>
 *   POST /landscape/assessments/generate               – generate this month's assessments for all active landscapes
 *   GET  /landscape/{lndscpAssmtId}/assessments        – read the details of an existing assessment
 *   GET  /landscape/{lndscpAssmtId}/dimensions         – read the landscape-config dimensions of an assessment
 *   GET  /landscape/{lndscpAssmtId}/{assmtDetailId}    – drill-down view of one assessment detail row
 * </pre>
 *
 * <p><b>Authentication:</b> the {@code X-EGRC-UserId} header is required.
 * A missing or blank value returns HTTP 401.
 */
@Slf4j
@RestController
@RequestMapping("/landscape")
@RequiredArgsConstructor
public class LandscapeAssmtDetailsController {

    private static final String MISSING_USER_MSG =
            "X-EGRC-UserId header is required and must not be blank.";

    private final LandscapeAssmtDetailsService service;
    private final BulkAssmtGenerationService bulkGenerationService;

    /**
     * Generates this month's assessment for every active landscape. Landscapes whose
     * config is ambiguous (multiple active rows effective today) or whose assessment
     * already exists are skipped and reported individually; generation continues for
     * the remaining landscapes.
     *
     * @param username value of the {@code X-EGRC-UserId} header, stored as CREATED_BY
     * @return HTTP 200 with per-landscape results; 401 when the header is missing/blank
     */
    @Operation(summary = "Generate assessments for all active landscapes",
            description = "Fetches all ACTIVE orl_lndscp_dim configs effective today, then generates "
                    + "this month's assessment for each landscape. Ambiguous configs and already-generated "
                    + "landscapes are skipped with a per-landscape status message; the rest continue.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bulk run completed; see per-landscape results"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/assessments/generate")
    public ResponseEntity<ApiResponse<BulkAssmtGenerationResponse>> generateForAllLandscapes(
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = "X-EGRC-UserId", required = false) String username) {

        if (username == null || username.isBlank()) {
            log.warn("POST /landscape/assessments/generate rejected — X-EGRC-UserId header missing or blank");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(MISSING_USER_MSG));
        }

        log.debug("POST /landscape/assessments/generate requested by user '{}'", username.trim());

        BulkAssmtGenerationResponse result =
                bulkGenerationService.generateForAllActiveLandscapes(username.trim());

        String message = result.getTotalLandscapes() == 0
                ? "No active landscapes are effective today — nothing to generate."
                : "Generated " + result.getGenerated() + " of " + result.getTotalLandscapes()
                        + " active landscape(s); " + result.getSkipped() + " skipped.";

        return ResponseEntity.ok(ApiResponse.successWithData(message, result));
    }

    /**
     * Returns the assessment header, landscape-config metadata, and all detail
     * rows for the given landscape assessment.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id} — obtained from the
     *                      {@code landscapeAssmtId} field in the
     *                      {@code GET /landscapes} response
     * @param username      value of the {@code X-EGRC-UserId} header
     * @return HTTP 200 with a {@link LandscapeAssmtDetailSummary} wrapper,
     *         HTTP 401 when the header is missing / blank, or HTTP 404 when
     *         no assessment exists for {@code lndscpAssmtId}
     */
    @Operation(summary = "Get landscape assessment details",
            description = "Returns the assessment header, landscape-config dimensions, "
                    + "and per-row detail items for the given landscape assessment id.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assessment details found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "lndscpAssmtId is not a valid number"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No assessment found for lndscpAssmtId"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{lndscpAssmtId}/assessments")
    public ResponseEntity<ApiResponse<LandscapeAssmtDetailSummary>> getByAssmtId(
            @Parameter(description = "orl_lndscp_assmt.id", required = true)
            @PathVariable Long lndscpAssmtId,
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = "X-EGRC-UserId", required = false) String username) {

        // ── Authentication guard ──────────────────────────────────────────────
        if (username == null || username.isBlank()) {
            log.warn("GET /landscape/{}/assessments rejected — X-EGRC-UserId header missing or blank",
                    lndscpAssmtId);
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("X-EGRC-UserId header is required and must not be blank."));
        }

        log.debug("GET /landscape/{}/assessments requested by user '{}'",
                lndscpAssmtId, username.trim());

        LandscapeAssmtDetailSummary summary = service.fetchByAssmtId(lndscpAssmtId);

        int count = summary.getAssessments().size();
        String message = count == 0
                ? "No assessment details found for landscape assessment id " + lndscpAssmtId + "."
                : count + " assessment detail(s) found for landscape assessment id "
                        + lndscpAssmtId + ".";

        log.debug("Returning {} detail(s) for lndscp_assmt_id={} to user '{}'",
                count, lndscpAssmtId, username.trim());

        return ResponseEntity.ok(
                ApiResponse.<LandscapeAssmtDetailSummary>successWithData(message, summary));
    }

    /**
     * Returns the landscape-config dimensions (risk areas, BU details, locations) for the
     * given landscape assessment.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     * @param username      value of the {@code X-EGRC-UserId} header
     * @return HTTP 200 with a {@link LandscapeDimensions} object; 401 when the header is
     *         missing/blank; 404 when the assessment (or its landscape config) does not exist
     */
    @Operation(summary = "Get landscape assessment dimensions",
            description = "Returns the landscape-config dimensions (risk areas, BU details and "
                    + "locations) for the given landscape assessment id.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dimensions found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "lndscpAssmtId is not a valid number"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No assessment found for lndscpAssmtId"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{lndscpAssmtId}/dimensions")
    public ResponseEntity<ApiResponse<LandscapeDimensions>> getDimensionsByAssmtId(
            @Parameter(description = "orl_lndscp_assmt.id", required = true)
            @PathVariable Long lndscpAssmtId,
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = "X-EGRC-UserId", required = false) String username) {

        if (username == null || username.isBlank()) {
            log.warn("GET /landscape/{}/dimensions rejected — X-EGRC-UserId header missing or blank",
                    lndscpAssmtId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(MISSING_USER_MSG));
        }

        log.debug("GET /landscape/{}/dimensions requested by user '{}'",
                lndscpAssmtId, username.trim());

        LandscapeDimensions dimensions = service.fetchDimensionsByAssmtId(lndscpAssmtId);

        return ResponseEntity.ok(ApiResponse.successWithData(
                "Dimensions found for landscape assessment id " + lndscpAssmtId + ".", dimensions));
    }

    /**
     * Returns the drill-down view of a single assessment detail row, including its
     * current-month, previous-month and live NRR snapshots.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     * @param assmtDetailId {@code orl_lndscp_assmt_details.id} — must belong to the assessment
     * @param username      value of the {@code X-EGRC-UserId} header
     * @return HTTP 200 with an {@link AssmtDetailResponse}; 401 when the header is
     *         missing/blank; 404 when the assessment does not exist or the detail row
     *         does not belong to it
     */
    @Operation(summary = "Get one assessment detail row (drill-down)",
            description = "Returns the full drill-down view of a single orl_lndscp_assmt_details row: "
                    + "current-month NRR, the matched previous-month NRR (via the assessment's "
                    + "PREV_ASSMT_NUM link), the live NRR snapshot, commentary and parsed GRC metrics.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Detail row found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "lndscpAssmtId or assmtDetailId is not a valid number"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Assessment not found, or detail row does not belong to it"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{lndscpAssmtId}/{assmtDetailId}")
    public ResponseEntity<ApiResponse<AssmtDetailResponse>> getDetailById(
            @Parameter(description = "orl_lndscp_assmt.id", required = true)
            @PathVariable Long lndscpAssmtId,
            @Parameter(description = "orl_lndscp_assmt_details.id", required = true)
            @PathVariable Long assmtDetailId,
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = "X-EGRC-UserId", required = false) String username) {

        if (username == null || username.isBlank()) {
            log.warn("GET /landscape/{}/{} rejected — X-EGRC-UserId header missing or blank",
                    lndscpAssmtId, assmtDetailId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure(MISSING_USER_MSG));
        }

        log.debug("GET /landscape/{}/{} requested by user '{}'",
                lndscpAssmtId, assmtDetailId, username.trim());

        AssmtDetailResponse detail = service.fetchDetailById(lndscpAssmtId, assmtDetailId);

        return ResponseEntity.ok(ApiResponse.successWithData(
                "Assessment detail " + assmtDetailId + " found.", detail));
    }
}
