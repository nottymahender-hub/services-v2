package com.dbs.mot.grc.service;

import com.dbs.mot.grc.common.exception.BadRequestException;
import com.dbs.mot.grc.common.exception.NotFoundException;
import com.dbs.mot.grc.dto.CalloutDimensions;
import com.dbs.mot.grc.dto.CalloutListResponse;
import com.dbs.mot.grc.dto.CalloutRequest;
import com.dbs.mot.grc.dto.CalloutResponse;
import com.dbs.mot.grc.dto.LandscapeAssmtRef;
import com.dbs.mot.grc.entity.OrlLndscpCallout;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.repository.OrlLndscpAssmtRepository;
import com.dbs.mot.grc.repository.OrlLndscpCalloutRepository;
import com.dbs.mot.grc.repository.OrlLndscpDimRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business logic for all {@code /landscape/{lndscpAssmtId}/callouts} endpoints.
 *
 * <p>Callouts belong to landscape <em>assessments</em> ({@code orl_lndscp_assmt}), not
 * directly to the landscape config ({@code orl_lndscp_dim}).
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>Reads use plain Spring Data JDBC {@code findById}/derived-query calls — no
 *       hand-written joins. {@link #getAssmtOrThrow(Long)} fetches the assessment once
 *       per request and is reused by every method that needs it (including dimension
 *       loading), avoiding a duplicate existence-check query.</li>
 *   <li>Updates and soft-deletes use {@link JdbcTemplate} to avoid a full entity reload
 *       and re-save (Spring Data JDBC's {@code save()} always issues a full REPLACE).</li>
 *   <li>Dimension validation fetches the parent {@code orl_lndscp_dim} row via
 *       {@link OrlLndscpDimRepository#findById} using the id carried by the assessment's
 *       {@code AggregateReference}, then parses {@code RISK_AREA} JSON and the
 *       {@code LOCATIONS}/{@code BIZ_UNITS} CSV strings — all in this service, not SQL.</li>
 *   <li>FK to {@code orl_lndscp_assmt} is modelled as {@link AggregateReference} on the
 *       {@link OrlLndscpCallout} entity — one-way, no cascade, no bidirectional navigation.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandscapeAssmtCalloutService {

    private static final String OTHERS = "Others";
    private static final String ALL    = "ALL";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final OrlLndscpCalloutRepository calloutRepository;
    private final OrlLndscpAssmtRepository   assmtRepository;
    private final OrlLndscpDimRepository     dimRepository;
    private final JdbcTemplate               jdbcTemplate;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns all active callouts for the given assessment together with the
     * valid dimension options (risk areas, locations, BU values).
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     * @throws NotFoundException if the assessment does not exist
     */
    public CalloutListResponse getCallouts(Long lndscpAssmtId) {
        log.debug("Fetching callouts for lndscp_assmt_id={}", lndscpAssmtId);
        LandscapeAssmtRef assmt = getAssmtOrThrow(lndscpAssmtId);

        CalloutDimensions dimensions = loadDimensions(assmt);
        List<OrlLndscpCallout> callouts =
                calloutRepository.findByLndscpAssmtIdAndDelFlgFalseOrderById(lndscpAssmtId);

        log.info("Found {} active callout(s) for lndscp_assmt_id={}", callouts.size(), lndscpAssmtId);

        return CalloutListResponse.builder()
                .dimensions(dimensions)
                .callouts(callouts.stream().map(this::toResponse).collect(Collectors.toList()))
                .build();
    }

    /**
     * Creates a new callout under the given landscape assessment after validating
     * field values against the landscape dimensions.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     * @param req           request body
     * @param username      value from the {@code X-EGRC-UserId} header
     * @throws NotFoundException   if the assessment does not exist
     * @throws BadRequestException if field values are not in the allowed sets
     */
    public CalloutResponse createCallout(Long lndscpAssmtId, CalloutRequest req, String username) {
        log.debug("Creating callout for lndscp_assmt_id={} by '{}'", lndscpAssmtId, username);
        LandscapeAssmtRef assmt = getAssmtOrThrow(lndscpAssmtId);

        CalloutDimensions dims = loadDimensions(assmt);
        validateRequest(req, dims);

        OrlLndscpCallout callout = OrlLndscpCallout.builder()
                .riskArea(req.getRiskArea())
                .locations(req.getLocations())
                .bizUnits(req.getBizUnits())
                // AggregateReference models the FK to orl_lndscp_assmt (one-way)
                .lndscpAssmtId(AggregateReference.to(lndscpAssmtId))
                .comment(truncate(req.getComment(), 400))
                .delFlg(false)
                .build();

        OrlLndscpCallout saved = calloutRepository.save(callout);
        log.info("Created callout id={} for lndscp_assmt_id={} by '{}'",
                saved.getId(), lndscpAssmtId, username);
        return toResponse(saved);
    }

    /**
     * Updates the editable fields of an existing callout after re-validating against
     * the landscape dimensions.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     * @param calloutId     {@code orl_lndscp_callout.id}
     * @param req           request body
     * @param username      value from the {@code X-EGRC-UserId} header
     * @throws NotFoundException   if the assessment or callout does not exist, or if the
     *                             callout belongs to a different assessment
     * @throws BadRequestException if field values are not in the allowed sets
     */
    public CalloutResponse updateCallout(Long lndscpAssmtId, Long calloutId,
                                         CalloutRequest req, String username) {
        log.debug("Updating callout id={} for lndscp_assmt_id={} by '{}'",
                calloutId, lndscpAssmtId, username);
        LandscapeAssmtRef assmt = getAssmtOrThrow(lndscpAssmtId);
        findCalloutForAssmt(calloutId, lndscpAssmtId);

        CalloutDimensions dims = loadDimensions(assmt);
        validateRequest(req, dims);

        String comment = truncate(req.getComment(), 400);
        jdbcTemplate.update(
                "UPDATE orl_lndscp_callout SET RISK_AREA=?, LOCATIONS=?, BIZ_UNITS=?, comment=? WHERE id=?",
                req.getRiskArea(), req.getLocations(), req.getBizUnits(), comment, calloutId);

        log.info("Updated callout id={} for lndscp_assmt_id={} by '{}'",
                calloutId, lndscpAssmtId, username);

        return CalloutResponse.builder()
                .id(calloutId)
                .riskArea(req.getRiskArea())
                .locations(req.getLocations())
                .bizUnits(req.getBizUnits())
                .comment(comment)
                .deleted(false)
                .build();
    }

    /**
     * Soft-deletes a callout by setting {@code DEL_FLG = TRUE}.
     * The row is retained in the database but excluded from all future GET responses.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     * @param calloutId     {@code orl_lndscp_callout.id}
     * @throws NotFoundException if the assessment or callout does not exist, or if the
     *                           callout belongs to a different assessment
     */
    public void deleteCallout(Long lndscpAssmtId, Long calloutId) {
        log.debug("Soft-deleting callout id={} for lndscp_assmt_id={}", calloutId, lndscpAssmtId);
        getAssmtOrThrow(lndscpAssmtId);
        findCalloutForAssmt(calloutId, lndscpAssmtId);

        jdbcTemplate.update("UPDATE orl_lndscp_callout SET DEL_FLG = TRUE WHERE id=?", calloutId);
        log.info("Soft-deleted callout id={} for lndscp_assmt_id={}", calloutId, lndscpAssmtId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches the assessment reference ({@code id} + {@code LNDSCP_NUM}) or throws.
     * Centralised here so every public method pays for exactly one assessment lookup.
     * Uses the reference projection rather than {@code findById} so it never triggers the
     * eager load of the assessment's {@code details} {@code MappedCollection} (which
     * carries large {@code GRC_METRICS} blobs the callout flow never reads).
     */
    private LandscapeAssmtRef getAssmtOrThrow(Long lndscpAssmtId) {
        return assmtRepository.findRefById(lndscpAssmtId)
                .orElseThrow(() -> new NotFoundException(
                        "Landscape assessment not found for id: " + lndscpAssmtId));
    }

    /**
     * Loads a callout by id and verifies it belongs to the given assessment.
     * Throws {@link NotFoundException} if either check fails.
     */
    private OrlLndscpCallout findCalloutForAssmt(Long calloutId, Long lndscpAssmtId) {
        OrlLndscpCallout callout = calloutRepository.findById(calloutId)
                .orElseThrow(() -> new NotFoundException("Callout not found for id: " + calloutId));

        // AggregateReference wraps the FK — extract the Long id with getId()
        Long storedAssmtId = callout.getLndscpAssmtId().getId();
        if (!lndscpAssmtId.equals(storedAssmtId)) {
            throw new NotFoundException("Callout id " + calloutId
                    + " does not belong to assessment id " + lndscpAssmtId);
        }
        return callout;
    }

    /**
     * Fetches the landscape dimension config for the given assessment and derives the
     * allowed option sets for RISK_AREA, LOCATIONS, and BIZ_UNITS.
     */
    private CalloutDimensions loadDimensions(LandscapeAssmtRef assmt) {
        Long dimId = assmt.lndscpNum();
        OrlLndscpDim dim = dimRepository.findById(dimId)
                .orElseThrow(() -> new NotFoundException(
                        "No landscape config found for id: " + dimId));

        List<String> riskAreaKeys = parseRiskAreaKeys(dim.getRiskArea());
        List<String> locations    = parseCsv(dim.getLocations());
        List<String> bizUnits     = parseCsv(dim.getBizUnits());

        // Append always-valid extra options per business rules
        List<String> validRiskAreas = new ArrayList<>(riskAreaKeys);
        validRiskAreas.add(OTHERS);

        List<String> validLocations = new ArrayList<>(locations);
        validLocations.add(OTHERS);
        validLocations.add(ALL);

        List<String> validBizUnits = new ArrayList<>(bizUnits);
        validBizUnits.add(OTHERS);
        validBizUnits.add(ALL);

        return CalloutDimensions.builder()
                .validRiskAreas(validRiskAreas)
                .validLocations(validLocations)
                .validBizUnits(validBizUnits)
                .build();
    }

    /**
     * Validates RISK_AREA (single value) and the CSV fields LOCATIONS and BIZ_UNITS
     * against the allowed sets derived from the landscape dimension config.
     *
     * @throws BadRequestException listing all invalid values found (collected, not fail-fast)
     */
    private void validateRequest(CalloutRequest req, CalloutDimensions dims) {
        List<String> errors = new ArrayList<>();

        if (!dims.getValidRiskAreas().contains(req.getRiskArea())) {
            errors.add("RISK_AREA '" + req.getRiskArea() + "' is not valid. Allowed: "
                    + dims.getValidRiskAreas());
        }

        validateCsvField("LOCATIONS", req.getLocations(), dims.getValidLocations(), errors);
        validateCsvField("BIZ_UNITS",  req.getBizUnits(),  dims.getValidBizUnits(),  errors);

        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors));
        }
    }

    private void validateCsvField(String fieldName, String csv,
                                   List<String> validValues, List<String> errors) {
        if (csv == null || csv.isBlank()) {
            errors.add(fieldName + " must contain at least one value.");
            return;
        }
        boolean hasAtLeastOne = false;
        for (String token : csv.split(",", -1)) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            hasAtLeastOne = true;
            if (!validValues.contains(t)) {
                errors.add(fieldName + " value '" + t + "' is not valid. Allowed: " + validValues);
            }
        }
        if (!hasAtLeastOne) {
            errors.add(fieldName + " must contain at least one non-blank value.");
        }
    }

    /** Parses the RISK_AREA JSON string and returns its keys in insertion order. */
    private List<String> parseRiskAreaKeys(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            Map<String, Object> map = MAPPER.readValue(
                    json, new TypeReference<LinkedHashMap<String, Object>>() {});
            return new ArrayList<>(map.keySet());
        } catch (Exception e) {
            log.warn("Failed to parse RISK_AREA JSON '{}': {}", json, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return null;
        return value.length() > maxLen ? value.substring(0, maxLen) : value;
    }

    private CalloutResponse toResponse(OrlLndscpCallout c) {
        return CalloutResponse.builder()
                .id(c.getId())
                .riskArea(c.getRiskArea())
                .locations(c.getLocations())
                .bizUnits(c.getBizUnits())
                .comment(c.getComment())
                .deleted(c.getDelFlg())
                .build();
    }
}
