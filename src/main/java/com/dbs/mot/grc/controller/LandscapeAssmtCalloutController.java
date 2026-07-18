package com.dbs.mot.grc.controller;

import com.dbs.mot.grc.common.dto.ApiResponse;
import com.dbs.mot.grc.dto.CalloutListResponse;
import com.dbs.mot.grc.dto.CalloutRequest;
import com.dbs.mot.grc.service.LandscapeAssmtCalloutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

/**
 * REST controller for callout CRUD operations scoped to a landscape <em>assessment</em>.
 *
 * <p>Callouts belong to {@code orl_lndscp_assmt}, so every endpoint is nested under
 * {@code /landscape/{lndscp_assmt_id}/callouts}. All endpoints require the
 * {@code X-EGRC-UserId} header (HTTP 401 if missing or blank).
 *
 * <p>Base path: {@code /landscape/{lndscpAssmtId}/callouts}
 */
@Slf4j
@RestController
@RequestMapping("/landscape")
@RequiredArgsConstructor
public class LandscapeAssmtCalloutController {

    private final LandscapeAssmtCalloutService calloutService;

    /**
     * Returns all active callouts for the given landscape assessment together with
     * the valid dimension options (risk areas, locations, BU values) for use in UI dropdowns.
     */
    @Operation(summary = "List callouts for an assessment",
            description = "Returns all active (non-deleted) callouts for the landscape assessment "
                    + "plus the valid RISK_AREA/LOCATIONS/BIZ_UNITS option sets.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Callouts found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No assessment found for lndscpAssmtId"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{lndscpAssmtId}/callouts")
    public ResponseEntity<ApiResponse<CalloutListResponse>> getCallouts(
            @PathVariable Long lndscpAssmtId,
            @RequestHeader(value = "X-EGRC-UserId", required = false) String username) {

        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("X-EGRC-UserId header is required."));
        }

        log.debug("GET /landscape/{}/callouts — user='{}'", lndscpAssmtId, username.trim());
        CalloutListResponse response = calloutService.getCallouts(lndscpAssmtId);
        int count = response.getCallouts().size();
        return ResponseEntity.ok(ApiResponse.successWithData(
                count + " callout(s) found for assessment id " + lndscpAssmtId + ".", response));
    }

    /**
     * Creates a new callout under the given landscape assessment.
     * Returns HTTP 201 with the persisted callout on success.
     */
    @Operation(summary = "Create a callout",
            description = "Creates a callout under the assessment. Stores the request sme as both "
                    + "SME and LAST_MODIFIED_SME, and records the comment in the history table.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Callout created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed (bad field values or missing required fields)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No assessment found for lndscpAssmtId"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/{lndscpAssmtId}/callouts")
    public ResponseEntity<ApiResponse<Void>> createCallout(
            @PathVariable Long lndscpAssmtId,
            @Valid @RequestBody CalloutRequest request,
            @RequestHeader(value = "X-EGRC-UserId", required = false) String username) {

        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("X-EGRC-UserId header is required."));
        }

        log.debug("POST /landscape/{}/callouts — user='{}'", lndscpAssmtId, username.trim());
        calloutService.createCallout(lndscpAssmtId, request, username.trim());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Callout created successfully."));
    }

    /**
     * Updates the RISK_AREA, LOCATIONS, BIZ_UNITS, comment and SME of an existing callout.
     * The callout must belong to the given landscape assessment. The callout's current SME
     * is shifted into LAST_MODIFIED_SME and the request sme becomes the new SME.
     */
    @Operation(summary = "Update a callout",
            description = "Updates a callout's fields. Shifts the current SME into LAST_MODIFIED_SME, "
                    + "sets SME to the request sme, and records the new comment in the history table.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Callout updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed (bad field values or missing required fields)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Assessment or callout not found, or callout belongs to another assessment"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PutMapping("/{lndscpAssmtId}/callouts/{calloutId}")
    public ResponseEntity<ApiResponse<Void>> updateCallout(
            @PathVariable Long lndscpAssmtId,
            @PathVariable Long calloutId,
            @Valid @RequestBody CalloutRequest request,
            @RequestHeader(value = "X-EGRC-UserId", required = false) String username) {

        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("X-EGRC-UserId header is required."));
        }

        log.debug("PUT /landscape/{}/callouts/{} — user='{}'",
                lndscpAssmtId, calloutId, username.trim());
        calloutService.updateCallout(lndscpAssmtId, calloutId, request, username.trim());
        return ResponseEntity.ok(ApiResponse.success("Callout updated successfully."));
    }

    /**
     * Soft-deletes a callout by setting {@code DEL_FLG = TRUE}.
     * The row is retained in the database but excluded from future GET responses.
     * The callout must belong to the given landscape assessment.
     */
    @Operation(summary = "Soft-delete a callout",
            description = "Sets DEL_FLG=TRUE so the callout is excluded from future GET responses; "
                    + "the row is retained.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Callout soft-deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or blank X-EGRC-UserId header"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Assessment or callout not found, or callout belongs to another assessment"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{lndscpAssmtId}/callouts/{calloutId}")
    public ResponseEntity<ApiResponse<Void>> deleteCallout(
            @PathVariable Long lndscpAssmtId,
            @PathVariable Long calloutId,
            @RequestHeader(value = "X-EGRC-UserId", required = false) String username) {

        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("X-EGRC-UserId header is required."));
        }

        log.debug("DELETE /landscape/{}/callouts/{} — user='{}'",
                lndscpAssmtId, calloutId, username.trim());
        calloutService.deleteCallout(lndscpAssmtId, calloutId);
        return ResponseEntity.ok(ApiResponse.success("Callout deleted successfully."));
    }
}
