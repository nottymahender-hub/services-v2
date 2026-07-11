package com.dbs.mot.grc.controller;

import com.dbs.mot.grc.common.dto.ApiResponse;
import com.dbs.mot.grc.dto.LandscapeAssmtSummary;
import com.dbs.mot.grc.service.LandscapeAssmtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller that exposes landscape assessment data.
 *
 * <pre>
 *   GET /landscapes  — returns all landscape assessment summaries
 * </pre>
 *
 * <p><b>Authentication:</b> every request must carry the {@code X-EGRC-UserId}
 * HTTP header. A missing or blank value returns HTTP 401 Unauthorized.
 * All other unhandled exceptions propagate to the global exception handler,
 * which returns HTTP 500.
 */
@Slf4j
@RestController
@RequestMapping("/landscapes")
@RequiredArgsConstructor
public class LandscapeAssmtController {

    private final LandscapeAssmtService service;

    /**
     * Returns all landscape assessments joined with their landscape names.
     *
     * @param username value of the {@code X-EGRC-UserId} header — identifies the caller
     *                 and is required for every request
     * @return HTTP 200 with an array of {@link LandscapeAssmtSummary} objects,
     *         or HTTP 401 when the header is missing / blank
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<LandscapeAssmtSummary>>> getAll(
            @RequestHeader(value = "X-EGRC-UserId", required = false) String username) {

        // ── Authentication guard ──────────────────────────────────────────────
        if (username == null || username.isBlank()) {
            log.warn("GET /landscapes rejected — X-EGRC-UserId header missing or blank");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.failure("X-EGRC-UserId header is required and must not be blank."));
        }

        log.debug("GET /landscapes requested by user '{}'", username.trim());

        List<LandscapeAssmtSummary> summaries = service.fetchAll();

        String message = summaries.isEmpty()
                ? "No landscape assessments found."
                : summaries.size() + " landscape assessment(s) found.";

        log.debug("Returning {} landscape assessment(s) to user '{}'",
                summaries.size(), username.trim());

        return ResponseEntity.ok(ApiResponse.<List<LandscapeAssmtSummary>>successWithData(message, summaries));
    }
}
