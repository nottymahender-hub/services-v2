package com.dbs.mot.grc.service;

import com.dbs.mot.grc.exception.BadRequestException;
import com.dbs.mot.grc.exception.NotFoundException;
import com.dbs.mot.grc.dto.CalloutRequest;
import com.dbs.mot.grc.dto.CalloutResponse;
import com.dbs.mot.grc.entity.OrlLndscpCallout;
import com.dbs.mot.grc.entity.OrlLndscpCalloutCommentHist;
import com.dbs.mot.grc.repository.OrlLndscpAssmtRepository;
import com.dbs.mot.grc.repository.OrlLndscpCalloutCommentHistRepository;
import com.dbs.mot.grc.repository.OrlLndscpCalloutRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Business logic for the callout endpoints. Callouts belong to landscape <em>assessments</em>
 * ({@code orl_lndscp_assmt}), not directly to the landscape config.
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>The owning assessment is checked for existence with {@code existsById} (an EXISTS query)
 *       rather than loaded — the callout flow never needs the assessment's fields or its detail
 *       {@code MappedCollection}.</li>
 *   <li>Create/update/soft-delete persist through the {@code CrudRepository}: create and update use
 *       {@code save()}, soft-delete flips {@code DEL_FLG} and saves. Update and delete already load
 *       the row (for the SME shift / ownership check), so {@code save()} adds no extra read, and
 *       {@link OrlLndscpCallout} has no child collection so {@code save()} is a plain single-row
 *       update.</li>
 *   <li>Field values are validated only by the request-body Bean Validation (presence + comment
 *       length); there is no validation of values against the landscape dimensions.</li>
 *   <li>FK to {@code orl_lndscp_assmt} is modelled as an {@link AggregateReference} on
 *       {@link OrlLndscpCallout} — one-way, no cascade.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandscapeAssmtCalloutService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final OrlLndscpCalloutRepository            calloutRepository;
    private final OrlLndscpCalloutCommentHistRepository commentHistRepository;
    private final OrlLndscpAssmtRepository              assmtRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns all active (non-deleted) callouts for the given assessment. Invoked only from the
     * assessment-details flow, which has already verified the assessment exists.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     */
    public List<CalloutResponse> getCallouts(Long lndscpAssmtId) {
        log.debug("Fetching callouts for lndscp_assmt_id={}", lndscpAssmtId);
        List<CalloutResponse> callouts =
                calloutRepository.findByLndscpAssmtIdAndDelFlgFalseOrderById(lndscpAssmtId).stream()
                        .map(this::toResponse)
                        .toList();
        log.info("Found {} active callout(s) for lndscp_assmt_id={}", callouts.size(), lndscpAssmtId);
        return callouts;
    }

    /**
     * Creates a new callout under the given landscape assessment.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     * @param req           request body (already Bean-Validated)
     * @param username      value from the {@code X-EGRC-UserId} header
     * @return the created callout (re-read so DB-managed timestamps are populated)
     * @throws NotFoundException if the assessment does not exist
     */
    public CalloutResponse createCallout(Long lndscpAssmtId, CalloutRequest req, String username) {
        log.debug("Creating callout for lndscp_assmt_id={} by '{}' (sme='{}')",
                lndscpAssmtId, username, req.getSme());
        requireAssmtExists(lndscpAssmtId);

        // CREATE_DT_TM is filled by the DB default — not set here.
        OrlLndscpCallout callout = OrlLndscpCallout.builder()
                .riskArea(req.getRiskArea())
                .locations(toJsonArray(req.getLocations()))
                .bizUnits(toJsonArray(req.getBizUnits()))
                .lndscpAssmtId(AggregateReference.to(lndscpAssmtId))
                .comment(req.getComment())
                .delFlg(false)
                // On create the SME both owns and is the last modifier.
                .sme(req.getSme())
                .lastModifiedSme(req.getSme())
                .build();

        OrlLndscpCallout saved = calloutRepository.save(callout);
        recordCommentHistory(saved.getId(), req.getComment(), req.getSme());
        log.info("Created callout id={} for lndscp_assmt_id={} by '{}'",
                saved.getId(), lndscpAssmtId, username);
        return toResponse(reload(saved.getId()));
    }

    /**
     * Updates the editable fields of an existing callout via {@code save()}. The current SME is
     * shifted into {@code LAST_MODIFIED_SME} and the request SME becomes the new owner.
     *
     * @return the updated callout (re-read so the DB-managed {@code UPDATE_DT_TM} is populated)
     * @throws NotFoundException if the assessment or callout does not exist, or the callout
     *                           belongs to a different assessment
     */
    public CalloutResponse updateCallout(Long lndscpAssmtId, Long calloutId,
                                         CalloutRequest req, String username) {
        log.debug("Updating callout id={} for lndscp_assmt_id={} by '{}' (sme='{}')",
                calloutId, lndscpAssmtId, username, req.getSme());
        requireAssmtExists(lndscpAssmtId);
        OrlLndscpCallout existing = findCalloutForAssmt(calloutId, lndscpAssmtId);

        String oldSme = existing.getSme();
        String newSme = req.getSme();

        // UPDATE_DT_TM is filled by the DB (ON UPDATE CURRENT_TIMESTAMP) — not set here.
        OrlLndscpCallout updated = existing.toBuilder()
                .riskArea(req.getRiskArea())
                .locations(toJsonArray(req.getLocations()))
                .bizUnits(toJsonArray(req.getBizUnits()))
                .comment(req.getComment())
                .sme(newSme)
                .lastModifiedSme(oldSme)
                .build();
        calloutRepository.save(updated);

        recordCommentHistory(calloutId, req.getComment(), newSme);
        log.info("Updated callout id={} for lndscp_assmt_id={} by '{}' (sme '{}' -> '{}')",
                calloutId, lndscpAssmtId, username, oldSme, newSme);
        return toResponse(reload(calloutId));
    }

    /** Re-reads a just-persisted callout so DB-managed timestamps are reflected in the response. */
    private OrlLndscpCallout reload(Long calloutId) {
        return calloutRepository.findById(calloutId)
                .orElseThrow(() -> new NotFoundException("Callout not found for id: " + calloutId));
    }

    /**
     * Soft-deletes a callout by setting {@code DEL_FLG = TRUE} and saving it. The row is retained
     * but excluded from all future active-callout queries.
     *
     * @throws NotFoundException if the assessment or callout does not exist, or the callout
     *                           belongs to a different assessment
     */
    public void deleteCallout(Long lndscpAssmtId, Long calloutId) {
        log.debug("Soft-deleting callout id={} for lndscp_assmt_id={}", calloutId, lndscpAssmtId);
        requireAssmtExists(lndscpAssmtId);
        OrlLndscpCallout existing = findCalloutForAssmt(calloutId, lndscpAssmtId);

        calloutRepository.save(existing.toBuilder().delFlg(true).build());
        log.info("Soft-deleted callout id={} for lndscp_assmt_id={}", calloutId, lndscpAssmtId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Verifies the owning assessment exists without loading it (or its detail collection). */
    private void requireAssmtExists(Long lndscpAssmtId) {
        if (!assmtRepository.existsById(lndscpAssmtId)) {
            throw new NotFoundException("Landscape assessment not found for id: " + lndscpAssmtId);
        }
    }

    /**
     * Loads a callout by id and verifies it belongs to the given assessment.
     * Throws {@link NotFoundException} if either check fails.
     */
    private OrlLndscpCallout findCalloutForAssmt(Long calloutId, Long lndscpAssmtId) {
        OrlLndscpCallout callout = calloutRepository.findById(calloutId)
                .orElseThrow(() -> new NotFoundException("Callout not found for id: " + calloutId));

        Long storedAssmtId = callout.getLndscpAssmtId().getId();
        if (!lndscpAssmtId.equals(storedAssmtId)) {
            throw new NotFoundException("Callout id " + calloutId
                    + " does not belong to assessment id " + lndscpAssmtId);
        }
        return callout;
    }

    /** Inserts one append-only comment-history row for the given callout (CREATE_DT_TM via DB default). */
    private void recordCommentHistory(Long calloutId, String comment, String sme) {
        commentHistRepository.save(OrlLndscpCalloutCommentHist.builder()
                .calloutId(AggregateReference.to(calloutId))
                .comment(comment)
                .sme(sme)
                .build());
        log.debug("Recorded comment history for callout id={} (sme='{}')", calloutId, sme);
    }

    /** Serialises a string list to a JSON array string for storage, e.g. {@code ["SG","HK"]}. */
    private String toJsonArray(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values == null ? Collections.emptyList() : values);
        } catch (JsonProcessingException e) {
            // Serialising a List<String> should never fail; surface as a client error, not a 500.
            throw new BadRequestException("Could not serialise value list: " + e.getMessage());
        }
    }

    /** Parses a stored JSON array string back to a string list; empty list on null/blank/parse error. */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON array '{}': {}", json, e.getMessage());
            return Collections.emptyList();
        }
    }

    private CalloutResponse toResponse(OrlLndscpCallout c) {
        return CalloutResponse.builder()
                .id(c.getId())
                .riskArea(c.getRiskArea())
                .locations(parseJsonArray(c.getLocations()))
                .bizUnits(parseJsonArray(c.getBizUnits()))
                .comment(c.getComment())
                .deleted(c.getDelFlg())
                .sme(c.getSme())
                .createdOn(c.getCreateDtTm())
                .updatedOn(c.getUpdateDtTm())
                .lastModifiedBy(c.getLastModifiedSme())
                .build();
    }
}
