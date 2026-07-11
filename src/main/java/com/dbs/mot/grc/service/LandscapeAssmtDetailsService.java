package com.dbs.mot.grc.service;

import com.dbs.mot.grc.common.exception.NotFoundException;
import com.dbs.mot.grc.dto.AssmtDetailResponse;
import com.dbs.mot.grc.dto.DimensionKey;
import com.dbs.mot.grc.dto.LandscapeAssmtDetailItem;
import com.dbs.mot.grc.dto.LandscapeAssmtDetailSummary;
import com.dbs.mot.grc.dto.LandscapeBuDetails;
import com.dbs.mot.grc.dto.LandscapeDimensions;
import com.dbs.mot.grc.dto.LiveNRRDetails;
import com.dbs.mot.grc.dto.MonthNRRDetails;
import com.dbs.mot.grc.entity.OrlLndscpAssmt;
import com.dbs.mot.grc.entity.OrlLndscpAssmtDetails;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.repository.OrlLndscpAssmtRepository;
import com.dbs.mot.grc.repository.OrlLndscpDimRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for the landscape-assessment read APIs:
 * <pre>
 *   GET /landscape/{lndscpAssmtId}/assessments     – full assessment with all detail rows
 *   GET /landscape/{lndscpAssmtId}/{assmtDetailId} – drill-down view of one detail row
 * </pre>
 *
 * <p>Previous-month values are derived at read time: the assessment's
 * {@code PREV_ASSMT_NUM} self-reference is followed to the previous assessment, whose
 * detail rows are matched by dimension key ({@code RISK_AREA, ORL_BU_NM_L2..L4,
 * LOCATION, category} — unique per assessment, so at most one match).
 *
 * <h3>No hand-written joins</h3>
 * <ol>
 *   <li>{@link OrlLndscpAssmtRepository#findById(Object)} — fetches the assessment
 *       together with its {@code details} {@code MappedCollection} (Spring Data JDBC
 *       issues a second, plain, no-join query for the child rows automatically).</li>
 *   <li>{@link OrlLndscpDimRepository#findById(Object)} — a second plain lookup for
 *       the parent landscape-config row, using the id carried by the assessment's
 *       {@code AggregateReference}.</li>
 * </ol>
 *
 * <p>Every derived value in the response (risk-area JSON parsing, CSV splitting,
 * the BU-hierarchy-level lookup, the {@code location}/{@code nrrOverlaid} rules,
 * the detail-row sort order, and the last-refreshed timestamp fallback) is computed
 * here in the service — no query in this class contains {@code JOIN}, {@code CASE},
 * or {@code COALESCE} logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandscapeAssmtDetailsService {

    private static final Set<String> DIRECT_LOCATION_CATEGORIES = Set.of("L2", "L3", "L4", "loc");
    private static final Set<String> GROUP_CATEGORIES = Set.of("grp_l2", "grp_l3", "grp_l4");
    private static final String GROUP_LOCATION = "Group";
    private static final String LOC_CATEGORY = "loc";
    private static final String OVERLAID_YES = "Y";
    private static final String OVERLAID_NO = "N";

    private final OrlLndscpAssmtRepository assmtRepository;
    private final OrlLndscpDimRepository   dimRepository;

    /** Shared ObjectMapper with duplicate-key detection enabled. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    /**
     * Fetches the full assessment-details response for the given assessment id.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     * @return the assembled response
     * @throws NotFoundException when no assessment (or its landscape config) exists
     */
    public LandscapeAssmtDetailSummary fetchByAssmtId(Long lndscpAssmtId) {
        log.debug("Fetching assessment details for lndscp_assmt_id={}", lndscpAssmtId);

        OrlLndscpAssmt assmt = getAssmtOrThrow(lndscpAssmtId);

        Long dimId = assmt.getLndscpNum().getId();
        OrlLndscpDim dim = dimRepository.findById(dimId)
                .orElseThrow(() -> {
                    log.warn("Landscape config id={} referenced by assessment id={} was not found",
                            dimId, lndscpAssmtId);
                    return new NotFoundException("Landscape config not found for id: " + dimId);
                });

        List<OrlLndscpAssmtDetails> rows = sortedDetails(assmt);
        log.debug("Assessment id={} has {} detail row(s)", lndscpAssmtId, rows.size());

        // Previous month's final ratings (OVRLY, falling back to CAL), keyed by dimension.
        Map<DimensionKey, String> prevFinalRatings = previousFinalRatings(assmt);

        LandscapeDimensions dimensions = toDimensions(dim);
        List<LandscapeAssmtDetailItem> items = rows.stream()
                .map(row -> toItem(row, dim.getBizUnitLvl(), prevFinalRatings.get(keyOf(row))))
                .collect(Collectors.toList());

        log.info("Returning {} assessment detail(s) for lndscp_assmt_id={}",
                items.size(), lndscpAssmtId);

        return LandscapeAssmtDetailSummary.builder()
                .lndscpName(dim.getLndscpNm())
                .lndscpAssmtId(assmt.getId())
                .lndscpAssmtPeriod(assmt.getAssmtPeriod())
                .lndscpAssmtStatus(assmt.getStatus())
                .lndscpLastRefreshed(resolveLastRefreshed(assmt))
                .dimensions(dimensions)
                .assessments(items)
                .build();
    }

    /**
     * Fetches the drill-down view of a single assessment detail row, including its
     * current-month, previous-month and live NRR snapshots.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     * @param assmtDetailId {@code orl_lndscp_assmt_details.id} — must belong to the assessment
     * @return the assembled detail response
     * @throws NotFoundException when the assessment does not exist, or the detail row
     *                           does not exist under that assessment
     */
    public AssmtDetailResponse fetchDetailById(Long lndscpAssmtId, Long assmtDetailId) {
        log.debug("Fetching detail id={} of assessment id={}", assmtDetailId, lndscpAssmtId);

        OrlLndscpAssmt assmt = getAssmtOrThrow(lndscpAssmtId);

        // Locating the row inside the aggregate's own collection also validates
        // ownership: a detail id from another assessment is a 404, not a data leak.
        OrlLndscpAssmtDetails row = detailsOf(assmt).stream()
                .filter(d -> assmtDetailId.equals(d.getId()))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Detail id={} not found under assessment id={}", assmtDetailId, lndscpAssmtId);
                    return new NotFoundException("Assessment detail not found for id " + assmtDetailId
                            + " under landscape assessment " + lndscpAssmtId + ".");
                });

        MonthNRRDetails current = MonthNRRDetails.builder()
                .nrrCalculated(row.getCalNetRiskRtng())
                .nrr(row.getOvrlyNetRiskRtng())
                .ctrlEffRtn(row.getCtrlEffRtn())
                .assmtPeriod(assmt.getAssmtPeriod())
                .build();

        MonthNRRDetails previous = previousMonthDetails(assmt, keyOf(row));

        LiveNRRDetails live = LiveNRRDetails.builder()
                .nrr(row.getLvNetRiskRtng())
                .lastRefreshed(row.getLvLstRfrshDtTm())
                .ctrlEffRtn(row.getLvCtrlEffRtn())
                .build();

        log.info("Returning detail id={} of assessment id={} (prev snapshot {})",
                assmtDetailId, lndscpAssmtId, previous != null ? "found" : "absent");

        return AssmtDetailResponse.builder()
                .id(row.getId())
                .riskArea(row.getRiskArea())
                .bu(resolveBuByCategory(row))
                .location(resolveLocation(row))
                .status(row.getStatus())
                .lastModified(row.getUpdateDtTm())
                .lastModifiedBy(row.getUpdatedBy())
                .currentMonthNRRDetails(current)
                .prevMonthNRRDetails(previous)
                .liveNRRDetails(live)
                .summary(row.getCommentary())
                .revisedSummary(row.getRevisedCommentary())
                .category(row.getCategory())
                .nrrOverlaid(row.getOvrlyNetRiskRtng() != null ? OVERLAID_YES : OVERLAID_NO)
                .overlayJustfkn(row.getOvrlyJstfkn())
                .grcMetrics(parseGrcMetrics(row))
                .build();
    }

    // ── Private derivation helpers ───────────────────────────────────────────

    private OrlLndscpAssmt getAssmtOrThrow(Long lndscpAssmtId) {
        return assmtRepository.findById(lndscpAssmtId)
                .orElseThrow(() -> {
                    log.warn("No landscape assessment found for id={}", lndscpAssmtId);
                    return new NotFoundException(
                            "Landscape assessment not found for id: " + lndscpAssmtId);
                });
    }

    /** The assessment's detail rows, never null. */
    private Set<OrlLndscpAssmtDetails> detailsOf(OrlLndscpAssmt assmt) {
        return assmt.getDetails() != null ? assmt.getDetails() : Set.of();
    }

    /** The dimension identity of a detail row, used to match rows across assessments. */
    private DimensionKey keyOf(OrlLndscpAssmtDetails row) {
        return new DimensionKey(row.getRiskArea(), row.getOrlBuNmL2(), row.getOrlBuNmL3(),
                row.getOrlBuNmL4(), row.getLocation(), row.getCategory());
    }

    /**
     * Follows {@code PREV_ASSMT_NUM} to the previous month's assessment, if any.
     * A dangling reference (row deleted despite the FK) is logged and treated as absent
     * rather than failing the request.
     */
    private Optional<OrlLndscpAssmt> loadPreviousAssmt(OrlLndscpAssmt assmt) {
        if (assmt.getPrevAssmtNum() == null) {
            return Optional.empty();
        }
        Long prevId = assmt.getPrevAssmtNum().getId();
        Optional<OrlLndscpAssmt> prev = assmtRepository.findById(prevId);
        if (prev.isEmpty()) {
            log.warn("PREV_ASSMT_NUM={} of assessment id={} points to a missing assessment",
                    prevId, assmt.getId());
        }
        return prev;
    }

    /**
     * Previous month's final rating per dimension key: {@code OVRLY_NET_RISK_RTNG}
     * when overlaid, else {@code CAL_NET_RISK_RTNG}. Empty when there is no previous
     * assessment. (Built with a plain loop — values may legitimately be null.)
     */
    private Map<DimensionKey, String> previousFinalRatings(OrlLndscpAssmt assmt) {
        Optional<OrlLndscpAssmt> prev = loadPreviousAssmt(assmt);
        if (prev.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<DimensionKey, String> ratings = new HashMap<>();
        for (OrlLndscpAssmtDetails d : detailsOf(prev.get())) {
            String rating = d.getOvrlyNetRiskRtng() != null ? d.getOvrlyNetRiskRtng() : d.getCalNetRiskRtng();
            ratings.put(keyOf(d), rating);
        }
        log.debug("Loaded {} previous rating(s) from assessment id={}",
                ratings.size(), prev.get().getId());
        return ratings;
    }

    /**
     * The previous month's NRR snapshot for the row matching the given dimension key,
     * or {@code null} when there is no previous assessment or no matching row.
     */
    private MonthNRRDetails previousMonthDetails(OrlLndscpAssmt assmt, DimensionKey key) {
        Optional<OrlLndscpAssmt> prev = loadPreviousAssmt(assmt);
        if (prev.isEmpty()) {
            return null;
        }
        return detailsOf(prev.get()).stream()
                .filter(d -> key.equals(keyOf(d)))
                .findFirst()          // the dimension unique index guarantees at most one match
                .map(d -> MonthNRRDetails.builder()
                        .nrrCalculated(d.getCalNetRiskRtng())
                        .nrr(d.getOvrlyNetRiskRtng())
                        .ctrlEffRtn(d.getCtrlEffRtn())
                        .assmtPeriod(prev.get().getAssmtPeriod())
                        .build())
                .orElseGet(() -> {
                    log.debug("No row in previous assessment id={} matches key {}",
                            prev.get().getId(), key);
                    return null;
                });
    }

    /**
     * Resolves the BU name from the row's own category (drill-down rule):
     * {@code L2/grp_l2 → ORL_BU_NM_L2}, {@code L3/grp_l3 → ORL_BU_NM_L3},
     * {@code L4/grp_l4 → ORL_BU_NM_L4}, {@code loc → "Group"}.
     */
    private String resolveBuByCategory(OrlLndscpAssmtDetails row) {
        String category = row.getCategory();
        if (category == null) {
            return null;
        }
        return switch (category) {
            case "L2", "grp_l2" -> row.getOrlBuNmL2();
            case "L3", "grp_l3" -> row.getOrlBuNmL3();
            case "L4", "grp_l4" -> row.getOrlBuNmL4();
            case LOC_CATEGORY   -> GROUP_LOCATION;
            default -> {
                log.warn("Unrecognised category '{}' for detail row id={} — bu resolved to null",
                        category, row.getId());
                yield null;
            }
        };
    }

    /**
     * Parses the row's {@code GRC_METRICS} JSON string into a tree so the response
     * carries a real JSON object instead of a double-encoded string. Null/blank or
     * unparseable content (the DB enforces json_valid, so the latter is unexpected)
     * yields {@code null} rather than failing the request.
     */
    private JsonNode parseGrcMetrics(OrlLndscpAssmtDetails row) {
        String json = row.getGrcMetrics();
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("GRC_METRICS of detail row id={} is not valid JSON — returning null: {}",
                    row.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * The assessment was last refreshed at {@code UPDATE_DT_TM} if it has been
     * updated at least once, otherwise at {@code CREATE_DT_TM}.
     */
    private LocalDateTime resolveLastRefreshed(OrlLndscpAssmt assmt) {
        return assmt.getUpdateDtTm() != null ? assmt.getUpdateDtTm() : assmt.getCreateDtTm();
    }

    /** Sorts the {@code MappedCollection} by (riskArea, location), replacing the old SQL ORDER BY. */
    private List<OrlLndscpAssmtDetails> sortedDetails(OrlLndscpAssmt assmt) {
        Set<OrlLndscpAssmtDetails> details = assmt.getDetails();
        if (details == null) return List.of();
        return details.stream()
                .sorted(Comparator
                        .comparing(OrlLndscpAssmtDetails::getRiskArea, Comparator.nullsLast(String::compareTo))
                        .thenComparing(OrlLndscpAssmtDetails::getLocation, Comparator.nullsLast(String::compareTo)))
                .collect(Collectors.toList());
    }

    private LandscapeDimensions toDimensions(OrlLndscpDim dim) {
        Map<String, List<String>> riskAreas = parseRiskAreaJson(dim.getRiskArea());

        LandscapeBuDetails buDetails = LandscapeBuDetails.builder()
                .lvl(dim.getBizUnitLvl())
                .bizUnits(parseCsv(dim.getBizUnits()))
                .build();

        return LandscapeDimensions.builder()
                .riskAreas(riskAreas.isEmpty() ? null : riskAreas)
                .buDetails(buDetails)
                .locations(parseCsv(dim.getLocations()))
                .build();
    }

    /**
     * Parses the RISK_AREA JSON string into a {@code Map<String, List<String>>}.
     * Returns an empty map on null/blank input or parse failure (logged as warning).
     */
    private Map<String, List<String>> parseRiskAreaJson(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, List<String>>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse RISK_AREA JSON: '{}' — {}", json, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private LandscapeAssmtDetailItem toItem(OrlLndscpAssmtDetails row, Integer bizUnitLvl,
                                            String prevAssmtFinalNRR) {
        String bu = resolveBu(row, bizUnitLvl);
        String location = resolveLocation(row);
        String nrrOverlaid = row.getOvrlyNetRiskRtng() != null ? OVERLAID_YES : OVERLAID_NO;

        log.debug("Mapping detail row id={} → bu='{}', location='{}', nrrOverlaid='{}'",
                row.getId(), bu, location, nrrOverlaid);

        return LandscapeAssmtDetailItem.builder()
                .id(row.getId())
                .riskArea(row.getRiskArea())
                .bu(bu)
                .location(location)
                .status(row.getStatus())
                .nrrCalculated(row.getCalNetRiskRtng())
                .nrr(row.getOvrlyNetRiskRtng())
                .riskRatingChange(row.getRiskRtngChge())
                .summary(row.getCommentary())
                .category(row.getCategory())
                .prevAssmtFinalNRR(prevAssmtFinalNRR)
                .nrrOverlaid(nrrOverlaid)
                .build();
    }

    /**
     * Resolves the BU name: the literal {@code "Group"} when {@code category} is
     * {@code loc}; otherwise the raw hierarchy column selected by the landscape's
     * configured BU level: 2 → {@code ORL_BU_NM_L2}, 3 → {@code ORL_BU_NM_L3},
     * 4 → {@code ORL_BU_NM_L4}. Returns {@code null} for any other level.
     */
    private String resolveBu(OrlLndscpAssmtDetails row, Integer bizUnitLvl) {
        if (LOC_CATEGORY.equals(row.getCategory())) return GROUP_LOCATION;
        if (bizUnitLvl == null) return null;
        return switch (bizUnitLvl) {
            case 2 -> row.getOrlBuNmL2();
            case 3 -> row.getOrlBuNmL3();
            case 4 -> row.getOrlBuNmL4();
            default -> null;
        };
    }

    /**
     * Resolves the display location: the row's own {@code LOCATION} for
     * BU-level or location-level categories ({@code L2, L3, L4, loc}), or the
     * literal {@code "Group"} for group-level categories
     * ({@code grp_l2, grp_l3, grp_l4}).
     */
    private String resolveLocation(OrlLndscpAssmtDetails row) {
        String category = row.getCategory();
        if (category == null) return row.getLocation();
        if (DIRECT_LOCATION_CATEGORIES.contains(category)) return row.getLocation();
        if (GROUP_CATEGORIES.contains(category)) return GROUP_LOCATION;
        log.warn("Unrecognised category '{}' for detail row id={} — falling back to raw LOCATION",
                category, row.getId());
        return row.getLocation();
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
