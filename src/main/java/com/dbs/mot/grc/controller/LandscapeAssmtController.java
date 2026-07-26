package com.dbs.mot.grc.controller;

import com.dbs.mot.grc.dto.ApiResponse;
import com.dbs.mot.grc.dto.AssmtDetailResponse;
import com.dbs.mot.grc.dto.BulkAssmtGenerationResponse;
import com.dbs.mot.grc.dto.CalloutRequest;
import com.dbs.mot.grc.dto.LandscapeAssmtDetailSummary;
import com.dbs.mot.grc.dto.LandscapeAssmtSummary;
import com.dbs.mot.grc.dto.SaveAssmtDetailRequest;
import com.dbs.mot.grc.exception.UnauthorizedException;
import com.dbs.mot.grc.service.BulkAssmtGenerationService;
import com.dbs.mot.grc.service.LandscapeAssmtCalloutService;
import com.dbs.mot.grc.service.LandscapeAssmtDetailsService;
import com.dbs.mot.grc.service.LandscapeAssmtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Single REST controller for all landscape-assessment operations: listing, generation, the
 * per-assessment detail view (with embedded dimensions and callouts), the single-row drill-down,
 * the detail overlay, and callout CRUD.
 *
 * <pre>
 *   GET    /landscape/assessments
 *   POST   /landscape/assessments/generate
 *   GET    /landscape/assessments/{lndscpAssmtId}
 *   GET    /landscape/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}
 *   POST   /landscape/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}/overlay
 *   POST   /landscape/assessment/{lndscpAssmtId}/callouts
 *   PUT    /landscape/assessment/{lndscpAssmtId}/callouts/{calloutId}
 *   DELETE /landscape/assessment/{lndscpAssmtId}/callouts/{calloutId}
 * </pre>
 *
 * <p><b>Authentication:</b> every endpoint requires the {@code X-EGRC-UserId} header; a missing
 * or blank value yields HTTP 401. All other error→HTTP mapping is centralised in the global
 * exception handler.
 */
@Slf4j
@RestController
@RequestMapping("/landscape")
@RequiredArgsConstructor
@Tag(name = "Landscape Assessments",
        description = "Listing, generation, details, overlay and callouts for landscape assessments.")
public class LandscapeAssmtController {

    private static final String USER_HEADER = "X-EGRC-UserId";

    private final LandscapeAssmtService assmtService;
    private final LandscapeAssmtDetailsService detailsService;
    private final BulkAssmtGenerationService bulkGenerationService;
    private final LandscapeAssmtCalloutService calloutService;

    // ── Listing ────────────────────────────────────────────────────────────────

    @Operation(summary = "List all landscape assessments",
            description = "Returns every landscape assessment summary enriched with its landscape name.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assessments returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/assessments")
    public ResponseEntity<ApiResponse<List<LandscapeAssmtSummary>>> getAllAssessments(
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = USER_HEADER, required = false) String username) {

        String user = requireUser(username);
        log.debug("GET /landscape/assessments requested by '{}'", user);

        List<LandscapeAssmtSummary> summaries = assmtService.fetchAll();
        String message = summaries.isEmpty()
                ? "No landscape assessments found."
                : summaries.size() + " landscape assessment(s) found.";
        return ResponseEntity.ok(ApiResponse.successWithData(message, summaries));
    }

    // ── Generation ───────────────────────────────────────────────────────────────

    @Operation(summary = "Generate assessments for all active landscapes",
            description = "Generates this month's assessment for every ACTIVE landscape effective today; "
                    + "ambiguous configs and already-generated landscapes are skipped per-landscape.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bulk run completed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/assessments/generate")
    public ResponseEntity<ApiResponse<BulkAssmtGenerationResponse>> generateForAllLandscapes(
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = USER_HEADER, required = false) String username) {

        String user = requireUser(username);
        log.debug("POST /landscape/assessments/generate requested by '{}'", user);

        BulkAssmtGenerationResponse result = bulkGenerationService.generateForAllActiveLandscapes(user);
        String message = result.getTotalLandscapes() == 0
                ? "No active landscapes are effective today — nothing to generate."
                : "Generated " + result.getGenerated() + " of " + result.getTotalLandscapes()
                        + " active landscape(s); " + result.getSkipped() + " skipped.";
        return ResponseEntity.ok(ApiResponse.successWithData(message, result));
    }

    // ── Assessment detail summary (with embedded dimensions + callouts) ────────────

    @Operation(summary = "Get landscape assessment details",
            description = "Returns the assessment header, its per-row detail items, the landscape-config "
                    + "dimensions and the assessment's callouts — all in one response.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Assessment details found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "lndscpAssmtId is not a valid number"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No assessment found for lndscpAssmtId"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/assessments/{lndscpAssmtId}")
    public ResponseEntity<ApiResponse<LandscapeAssmtDetailSummary>> getAssessmentById(
            @Parameter(description = "orl_lndscp_assmt.id", required = true)
            @PathVariable Long lndscpAssmtId,
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = USER_HEADER, required = false) String username) {

        String user = requireUser(username);
        log.debug("GET /landscape/assessments/{} requested by '{}'", lndscpAssmtId, user);

        LandscapeAssmtDetailSummary summary = detailsService.fetchByAssmtId(lndscpAssmtId);
        int count = summary.getAssessments().size();
        String message = count == 0
                ? "No assessment details found for landscape assessment id " + lndscpAssmtId + "."
                : count + " assessment detail(s) found for landscape assessment id " + lndscpAssmtId + ".";
        return ResponseEntity.ok(ApiResponse.successWithData(message, summary));
    }

    // ── Single-row drill-down ──────────────────────────────────────────────────────

    @Operation(summary = "Get one assessment detail row (drill-down)",
            description = "Returns the full drill-down for a single detail row: current-month, matched "
                    + "previous-month and live NRR snapshots, commentary and GRC metrics.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Detail row found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "A path id is not a valid number"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Assessment not found, or detail row does not belong to it"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}")
    public ResponseEntity<ApiResponse<AssmtDetailResponse>> getAssessmentDetail(
            @Parameter(description = "orl_lndscp_assmt.id", required = true)
            @PathVariable Long lndscpAssmtId,
            @Parameter(description = "orl_lndscp_assmt_details.id", required = true)
            @PathVariable Long assmtDetailId,
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = USER_HEADER, required = false) String username) {

        String user = requireUser(username);
        log.debug("GET /landscape/assessment/{}/assessmentDetail/{} requested by '{}'",
                lndscpAssmtId, assmtDetailId, user);

        AssmtDetailResponse detail = detailsService.fetchDetailById(lndscpAssmtId, assmtDetailId);
        return ResponseEntity.ok(ApiResponse.successWithData(
                "Assessment detail " + assmtDetailId + " found.", detail));
    }

    // ── Detail overlay ───────────────────────────────────────────────────────────

    @Operation(summary = "Save an assessment detail overlay",
            description = "Persists REVISED_COMMENTARY, OVRLY_NET_RISK_RTNG and OVRLY_JSTFKN for a detail "
                    + "row and stamps UPDATED_BY. Allowed only when the detail is in 'Open' status.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Overlay saved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Assessment or detail not found, or detail does not belong to the assessment"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Detail is not in 'Open' status"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}/overlay")
    public ResponseEntity<ApiResponse<Void>> saveAssessmentDetailOverlay(
            @Parameter(description = "orl_lndscp_assmt.id", required = true)
            @PathVariable Long lndscpAssmtId,
            @Parameter(description = "orl_lndscp_assmt_details.id", required = true)
            @PathVariable Long assmtDetailId,
            @Valid @RequestBody SaveAssmtDetailRequest request,
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = USER_HEADER, required = false) String username) {

        String user = requireUser(username);
        log.debug("POST /landscape/assessment/{}/assessmentDetail/{}/overlay requested by '{}'",
                lndscpAssmtId, assmtDetailId, user);

        detailsService.saveOverlay(lndscpAssmtId, assmtDetailId, request, user);
        return ResponseEntity.ok(ApiResponse.success(
                "Assessment detail " + assmtDetailId + " saved successfully."));
    }

    // ── Callouts ─────────────────────────────────────────────────────────────────

    @Operation(summary = "Create a callout", description = "Creates a callout under the assessment.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Callout created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No assessment found for lndscpAssmtId"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/assessment/{lndscpAssmtId}/callouts")
    public ResponseEntity<ApiResponse<Void>> createCallout(
            @Parameter(description = "orl_lndscp_assmt.id", required = true)
            @PathVariable Long lndscpAssmtId,
            @Valid @RequestBody CalloutRequest request,
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = USER_HEADER, required = false) String username) {

        String user = requireUser(username);
        log.debug("POST /landscape/assessment/{}/callouts requested by '{}'", lndscpAssmtId, user);

        calloutService.createCallout(lndscpAssmtId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Callout created successfully."));
    }

    @Operation(summary = "Update a callout", description = "Updates a callout's fields and shifts the SME.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Callout updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Assessment or callout not found, or callout belongs to another assessment"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PutMapping("/assessment/{lndscpAssmtId}/callouts/{calloutId}")
    public ResponseEntity<ApiResponse<Void>> updateCallout(
            @Parameter(description = "orl_lndscp_assmt.id", required = true)
            @PathVariable Long lndscpAssmtId,
            @Parameter(description = "orl_lndscp_callout.id", required = true)
            @PathVariable Long calloutId,
            @Valid @RequestBody CalloutRequest request,
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = USER_HEADER, required = false) String username) {

        String user = requireUser(username);
        log.debug("PUT /landscape/assessment/{}/callouts/{} requested by '{}'",
                lndscpAssmtId, calloutId, user);

        calloutService.updateCallout(lndscpAssmtId, calloutId, request, user);
        return ResponseEntity.ok(ApiResponse.success("Callout updated successfully."));
    }

    @Operation(summary = "Soft-delete a callout",
            description = "Sets DEL_FLG=TRUE so the callout is excluded from future GET responses.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Callout soft-deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Assessment or callout not found, or callout belongs to another assessment"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/assessment/{lndscpAssmtId}/callouts/{calloutId}")
    public ResponseEntity<ApiResponse<Void>> deleteCallout(
            @Parameter(description = "orl_lndscp_assmt.id", required = true)
            @PathVariable Long lndscpAssmtId,
            @Parameter(description = "orl_lndscp_callout.id", required = true)
            @PathVariable Long calloutId,
            @Parameter(description = "Operator identity", required = true)
            @RequestHeader(value = USER_HEADER, required = false) String username) {

        String user = requireUser(username);
        log.debug("DELETE /landscape/assessment/{}/callouts/{} requested by '{}'",
                lndscpAssmtId, calloutId, user);

        calloutService.deleteCallout(lndscpAssmtId, calloutId);
        return ResponseEntity.ok(ApiResponse.success("Callout deleted successfully."));
    }

    // ── Helper ───────────────────────────────────────────────────────────────────

    /** Returns the trimmed operator id, or throws {@link UnauthorizedException} when missing/blank. */
    private String requireUser(String username) {
        if (username == null || username.isBlank()) {
            throw new UnauthorizedException("X-EGRC-UserId header is required and must not be blank.");
        }
        return username.trim();
    }
}
