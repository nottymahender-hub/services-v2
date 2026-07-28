package com.dbs.mot.grc.service;

import com.dbs.mot.grc.enums.DetailStatus;
import com.dbs.mot.grc.enums.LevelCategory;
import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.enums.PersistableEnum;
import com.dbs.mot.grc.exception.ConflictException;
import com.dbs.mot.grc.exception.NotFoundException;
import com.dbs.mot.grc.util.RiskAreaParser;
import com.dbs.mot.grc.dto.AssmtDetailResponse;
import com.dbs.mot.grc.dto.AssmtHeader;
import com.dbs.mot.grc.dto.CalloutResponse;
import com.dbs.mot.grc.dto.DimensionKey;
import com.dbs.mot.grc.dto.LandscapeAssmtDetailItem;
import com.dbs.mot.grc.dto.LandscapeAssmtDetailSummary;
import com.dbs.mot.grc.dto.LandscapeBuDetails;
import com.dbs.mot.grc.dto.LandscapeDimensions;
import com.dbs.mot.grc.dto.LiveNRRDetails;
import com.dbs.mot.grc.dto.MonthNRRDetails;
import com.dbs.mot.grc.dto.RiskAreaGroup;
import com.dbs.mot.grc.dto.SaveAssmtDetailRequest;
import com.dbs.mot.grc.entity.FactOrl;
import com.dbs.mot.grc.entity.OrlLndscpAssmt;
import com.dbs.mot.grc.entity.OrlLndscpAssmtDetails;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.repository.FactOrlRepository;
import com.dbs.mot.grc.repository.OrlLndscpAssmtDetailsRepository;
import com.dbs.mot.grc.repository.OrlLndscpAssmtRepository;
import com.dbs.mot.grc.repository.OrlLndscpDimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read logic for the landscape-assessment APIs ({@code /landscape/{id}/assessments},
 * {@code /dimensions} and {@code /{id}/{detailId}}).
 *
 * <p>Assessment detail rows are thin; computed values live in {@code fact_orl} and are matched
 * at read time by dimension key ({@code RISK_AREA, ORL_BU_NM_L2..L4, LOCATION}). The list
 * endpoint fetches only the facts whose dimension key matches the assessment's own detail rows
 * (a DB-side semi-join — {@code FactOrlRepository.findMatchingByAssmtDetails}), so it never loads
 * a whole business date's facts; the single-row drill-down fetches each fact directly by
 * (biz date, dimension key). Net risk ratings are returned in their display form
 * (e.g. {@code "Medium-Low Risk"}); other enum columns are returned as their stored value.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandscapeAssmtDetailsService {

    private static final Set<LevelCategory> DIRECT_LOCATION_CATEGORIES =
            Set.of(LevelCategory.L2, LevelCategory.L3, LevelCategory.L4, LevelCategory.LOC);
    private static final Set<LevelCategory> GROUP_CATEGORIES =
            Set.of(LevelCategory.GRP_L2, LevelCategory.GRP_L3, LevelCategory.GRP_L4);
    private static final String GROUP_LOCATION = "Group";
    private static final String OVERLAID_YES = "Y";
    private static final String OVERLAID_NO = "N";

    private final OrlLndscpAssmtRepository        assmtRepository;
    private final OrlLndscpAssmtDetailsRepository detailsRepository;
    private final OrlLndscpDimRepository          dimRepository;
    private final FactOrlRepository               factRepository;
    private final RiskAreaParser                  riskAreaParser;
    private final GrcMetricsService               grcMetricsService;
    private final LandscapeAssmtCalloutService    calloutService;

    /**
     * Returns the assessment header plus every detail row, each enriched with its current-month
     * fact values, previous-month final rating, and risk-area group/clusters.
     *
     * @throws NotFoundException if the assessment or its landscape config does not exist
     */
    public LandscapeAssmtDetailSummary fetchByAssmtId(Long lndscpAssmtId) {
        log.debug("Fetching assessment details for lndscp_assmt_id={}", lndscpAssmtId);

        OrlLndscpAssmt assmt = getAssmtOrThrow(lndscpAssmtId);
        OrlLndscpDim dim = getDimOrThrow(assmt.getLndscpNum().getId(), lndscpAssmtId);

        List<OrlLndscpAssmtDetails> rows = detailRows(assmt);

        Map<DimensionKey, FactOrl> currentFacts = factsForAssmt(assmt.getId(), bizDateOf(assmt));
        Map<DimensionKey, NetRiskRating> prevFinalRatings = previousFinalRatings(assmt);

        // Parse the landscape's RISK_AREA JSON once and reuse it for every view below
        // (per-row group/cluster lookup + the embedded dimensions), rather than re-parsing it 3×.
        List<RiskAreaGroup> riskAreaGroups = riskAreaParser.parseQuietly(dim.getRiskArea());
        Map<String, RiskAreaParser.AreaLookup> riskAreaLookup =
                riskAreaParser.lookupByRiskArea(riskAreaGroups);

        List<LandscapeAssmtDetailItem> items = rows.stream()
                .map(row -> toItem(row, dim.getBizUnitLvl(),
                        currentFacts.get(keyOf(row)), prevFinalRatings.get(keyOf(row)),
                        riskAreaLookup.get(row.getRiskArea())))
                .toList();

        // Dimensions are built from the already-loaded config; callouts are one additional read.
        // Both are embedded so a single call returns the assessment, its dimensions and callouts.
        List<CalloutResponse> callouts = calloutService.getCallouts(lndscpAssmtId);

        log.info("Returning {} assessment detail(s) plus {} callout(s) for lndscp_assmt_id={}",
                items.size(), callouts.size(), lndscpAssmtId);

        return LandscapeAssmtDetailSummary.builder()
                .lndscpName(dim.getLndscpNm())
                .lndscpAssmtId(assmt.getId())
                .lndscpAssmtPeriod(assmt.getAssmtPeriod())
                .lndscpAssmtStatus(PersistableEnum.dbValue(assmt.getStatus()))
                .lndscpLastRefreshed(factRepository.findMaxBizDt())
                .lndscpLastModifiedOn(resolveLastModifiedOn(assmt))
                .lndscpLastModifiedBy(resolveLastModifiedBy(assmt))
                .dimensions(toDimensions(dim, riskAreaGroups))
                .callouts(callouts)
                .assessments(items)
                .build();
    }

    /**
     * Returns the drill-down view of one detail row: its current-month, previous-month and
     * live NRR snapshots, all sourced from {@code fact_orl}.
     *
     * @throws NotFoundException if the assessment does not exist, or the detail row does not
     *                           belong to it
     */
    public AssmtDetailResponse fetchDetailById(Long lndscpAssmtId, Long assmtDetailId) {
        log.debug("Fetching detail id={} of assessment id={}", assmtDetailId, lndscpAssmtId);

        // Header projection only — avoids eagerly loading the assessment's whole detail collection.
        AssmtHeader assmt = assmtRepository.findHeaderById(lndscpAssmtId)
                .orElseThrow(() -> new NotFoundException(
                        "Landscape assessment not found for id: " + lndscpAssmtId));

        // The detail id is the primary key, so it uniquely identifies the row on its own.
        OrlLndscpAssmtDetails row = detailsRepository.findById(assmtDetailId)
                .orElseThrow(() -> new NotFoundException(
                        "Assessment detail not found for id: " + assmtDetailId));

        DimensionKey key = keyOf(row);
        LocalDate currentBizDt = assmt.bizDt();
        FactOrl currentFact = factFor(currentBizDt, key).orElse(null);
        // Assembled once; reused for the live block when the latest business date == currentBizDt.
        Map<String, Object> currentMetrics = grcMetricsService.forBizDate(currentBizDt, key);

        String currentNrrCalculated =
                NetRiskRating.display(currentFact != null ? currentFact.getCalNetRiskRtng() : null);
        MonthNRRDetails current = MonthNRRDetails.builder()
                .nrrCalculated(currentNrrCalculated)
                .nrr(resolveNrr(row.getOvrlyNetRiskRtng(), currentNrrCalculated))
                .nrrOverlaid(row.getOvrlyNetRiskRtng() != null ? OVERLAID_YES : OVERLAID_NO)
                .overlayJstfkn(row.getOvrlyJstfkn())
                .ctrlEffRtn(currentFact != null ? currentFact.getCtrlEffRtn() : null)
                .assmtPeriod(assmt.assmtPeriod())
                .grcMetrics(currentMetrics)
                .commentry(currentFact != null ? currentFact.getCommentary() : null)
                .revisedCommentry(row.getRevisedCommentary())
                .build();

        MonthNRRDetails previous = previousMonthDetails(assmt, key);

        // The "live" snapshot is the fact row on the latest business date across fact_orl, matched
        // to this dimension. That MAX(biz_dt) is read once and reused for the response's top-level
        // lastRefreshed. When it equals the assessment's own biz_dt, live == current, so the
        // already-fetched fact and metrics are reused instead of issuing the same queries again.
        LocalDate maxBizDt = factRepository.findMaxBizDt();
        LiveNRRDetails live = resolveLive(maxBizDt, currentBizDt, key, currentFact, currentMetrics);

        log.info("Returning detail id={} of assessment id={} (prev {}, live {})",
                assmtDetailId, lndscpAssmtId,
                previous != null ? "found" : "absent", live != null ? "found" : "absent");

        return AssmtDetailResponse.builder()
                .id(row.getId())
                .landscapeAssessmentId(assmt.id())
                .landscapeId(assmt.lndscpNum())
                .riskArea(row.getRiskArea())
                .bu(resolveBuByCategory(row))
                .location(resolveLocation(row))
                .status(PersistableEnum.dbValue(row.getStatus()))
                .lastModifiedOn(row.getUpdateDtTm() != null ? row.getUpdateDtTm() : row.getCreateDtTm())
                .lastModifiedBy(row.getUpdatedBy() != null ? row.getUpdatedBy() : row.getCreatedBy())
                .lastRefreshed(maxBizDt)
                .currentMonthNRRDetails(current)
                .prevMonthNRRDetails(previous)
                .liveNRRDetails(live)
                .category(PersistableEnum.dbValue(row.getCategory()))
                .build();
    }

    /**
     * Saves the analyst overlay (revised commentary, overlaid net risk rating, overlay
     * justification) onto an assessment detail row and stamps the auditor. The detail id is the
     * primary key, so the row is located by id alone; the assessment is validated only for
     * existence. {@code UPDATE_DT_TM} is filled by the DB ({@code ON UPDATE CURRENT_TIMESTAMP}).
     *
     * @throws NotFoundException if the assessment or the detail does not exist
     * @throws ConflictException if the detail is not in {@code Open} status
     */
    @Transactional
    public void saveOverlay(Long lndscpAssmtId, Long assmtDetailId,
                            SaveAssmtDetailRequest request, String username) {
        log.debug("Saving overlay for detail id={} of assessment id={} by '{}'",
                assmtDetailId, lndscpAssmtId, username);

        if (!assmtRepository.existsById(lndscpAssmtId)) {
            throw new NotFoundException("Landscape assessment not found for id: " + lndscpAssmtId);
        }

        OrlLndscpAssmtDetails detail = detailsRepository.findById(assmtDetailId)
                .orElseThrow(() -> new NotFoundException(
                        "Assessment detail not found for id: " + assmtDetailId));

        if (detail.getStatus() != DetailStatus.OPEN) {
            throw new ConflictException("Save is not allowed: assessment detail " + assmtDetailId
                    + " is not in '" + DetailStatus.OPEN.getDbValue() + "' status (current: "
                    + PersistableEnum.dbValue(detail.getStatus()) + ").");
        }

        String overlaidNrr = blankToNull(request.getOverlaidNRR());
        detailsRepository.save(detail.toBuilder()
                .revisedCommentary(blankToNull(request.getRevisedCommentry()))
                .ovrlyNetRiskRtng(overlaidNrr != null ? NetRiskRating.fromDbValue(overlaidNrr) : null)
                .ovrlyJstfkn(blankToNull(request.getOverlayJstfkn()))
                .updatedBy(username)
                .build());

        log.info("Saved overlay for detail id={} of assessment id={} by '{}'",
                assmtDetailId, lndscpAssmtId, username);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    /**
     * The net risk rating to display: the analyst overlay when set, otherwise the calculated
     * rating. Shared by the list items and the drill-down's current/previous-month blocks.
     */
    private String resolveNrr(NetRiskRating overlay, String nrrCalculated) {
        return overlay != null ? NetRiskRating.display(overlay) : nrrCalculated;
    }

    // ── fact_orl lookups ─────────────────────────────────────────────────────

    /** The business date used to match {@code fact_orl}/module snapshots: the assessment's {@code biz_dt}. */
    private LocalDate bizDateOf(OrlLndscpAssmt assmt) {
        return assmt.getBizDt();
    }

    /**
     * The {@code fact_orl} rows for one assessment on a business date, indexed by dimension key.
     * Fetches <em>only</em> the facts whose dimension key matches one of the assessment's detail
     * rows (a DB-side semi-join) rather than every fact for the date — so an assessment with N
     * detail rows pulls at most N facts, regardless of how large {@code fact_orl} is for that date.
     */
    private Map<DimensionKey, FactOrl> factsForAssmt(Long lndscpAssmtId, LocalDate bizDt) {
        if (lndscpAssmtId == null || bizDt == null) {
            return Collections.emptyMap();
        }
        List<FactOrl> facts = factRepository.findMatchingByAssmtDetails(lndscpAssmtId, bizDt);
        Map<DimensionKey, FactOrl> byKey = new LinkedHashMap<>();
        for (FactOrl fact : facts) {
            byKey.putIfAbsent(factKeyOf(fact), fact);
        }
        log.debug("Loaded {} matching fact_orl row(s) for assmt id={} on biz_dt={}",
                byKey.size(), lndscpAssmtId, bizDt);
        return byKey;
    }

    /** The single {@code fact_orl} row for a (business date, dimension key), if present. */
    private Optional<FactOrl> factFor(LocalDate bizDt, DimensionKey key) {
        if (bizDt == null) {
            return Optional.empty();
        }
        return factRepository.findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
                bizDt, key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location());
    }

    /**
     * The live snapshot for a dimension: the {@code fact_orl} row on {@code maxBizDt} (the latest
     * business date across {@code fact_orl}) matching the dimension. Fetched via the same by-date
     * path as the current/previous month, so no separate "latest per dimension" query is needed.
     *
     * @return {@code null} when there is no fact data (empty table) or the dimension has no row on
     *         {@code maxBizDt}
     */
    private LiveNRRDetails liveDetails(LocalDate maxBizDt, DimensionKey key) {
        FactOrl latest = factFor(maxBizDt, key).orElse(null);
        if (latest == null) {
            log.debug("No live fact_orl row for key {} on biz_dt={}", key, maxBizDt);
            return null;
        }
        return liveFrom(latest, maxBizDt, grcMetricsService.forBizDate(maxBizDt, key));
    }

    /**
     * Resolves the live snapshot, avoiding a duplicate fact + module-metric lookup when the latest
     * business date is the assessment's own {@code biz_dt} (live and current are then identical):
     * the already-fetched current fact and metrics are reused. Otherwise the live row is fetched
     * for {@code maxBizDt}.
     *
     * @return {@code null} when there is no fact data, or the dimension has no row on {@code maxBizDt}
     */
    private LiveNRRDetails resolveLive(LocalDate maxBizDt, LocalDate currentBizDt, DimensionKey key,
                                       FactOrl currentFact, Map<String, Object> currentMetrics) {
        if (maxBizDt == null) {
            return null;
        }
        if (maxBizDt.equals(currentBizDt)) {
            if (currentFact == null) {
                log.debug("No live fact_orl row for key {} on biz_dt={} (same as current)", key, maxBizDt);
                return null;
            }
            log.debug("Live snapshot reuses the current-month fact for key {} on biz_dt={}", key, maxBizDt);
            return liveFrom(currentFact, maxBizDt, currentMetrics);
        }
        return liveDetails(maxBizDt, key);
    }

    /** Builds a {@link LiveNRRDetails} from an already-loaded fact row and its GRC metrics. */
    private LiveNRRDetails liveFrom(FactOrl fact, LocalDate maxBizDt, Map<String, Object> grcMetrics) {
        return LiveNRRDetails.builder()
                .nrr(NetRiskRating.display(fact.getCalNetRiskRtng()))
                .nrrOverlaid(OVERLAID_NO)
                .overlayJstfkn(null)
                .lastRefreshed(maxBizDt)
                .ctrlEffRtn(fact.getCtrlEffRtn())
                .grcMetrics(grcMetrics)
                .commentry(fact.getCommentary())
                .build();
    }

    // ── Derivation helpers ───────────────────────────────────────────────────

    private OrlLndscpAssmt getAssmtOrThrow(Long lndscpAssmtId) {
        return assmtRepository.findById(lndscpAssmtId)
                .orElseThrow(() -> new NotFoundException(
                        "Landscape assessment not found for id: " + lndscpAssmtId));
    }

    private OrlLndscpDim getDimOrThrow(Long dimId, Long lndscpAssmtId) {
        return dimRepository.findById(dimId)
                .orElseThrow(() -> {
                    log.warn("Landscape config id={} referenced by assessment id={} was not found",
                            dimId, lndscpAssmtId);
                    return new NotFoundException("Landscape config not found for id: " + dimId);
                });
    }

    /** The assessment's detail rows (no explicit ordering applied). */
    private List<OrlLndscpAssmtDetails> detailRows(OrlLndscpAssmt assmt) {
        return assmt.getDetails() != null ? List.copyOf(assmt.getDetails()) : List.of();
    }

    private DimensionKey keyOf(OrlLndscpAssmtDetails row) {
        return new DimensionKey(row.getRiskArea(), row.getOrlBuNmL2(), row.getOrlBuNmL3(),
                row.getOrlBuNmL4(), row.getLocation());
    }

    private DimensionKey factKeyOf(FactOrl fact) {
        return new DimensionKey(fact.getRiskArea(), fact.getOrlBuNmL2(), fact.getOrlBuNmL3(),
                fact.getOrlBuNmL4(), fact.getLocation());
    }

    /**
     * Previous month's final rating per dimension key: the prior detail row's
     * {@code OVRLY_NET_RISK_RTNG}, falling back to the prior month's {@code CAL_NET_RISK_RTNG}.
     * Empty when there is no previous assessment.
     */
    private Map<DimensionKey, NetRiskRating> previousFinalRatings(OrlLndscpAssmt assmt) {
        if (assmt.getPrevAssmtNum() == null) {
            return Collections.emptyMap();
        }
        Long prevId = assmt.getPrevAssmtNum().getId();
        // Load the previous assessment aggregate: its detail rows (the @MappedCollection) carry the
        // overlays we need, and the root carries its business date. A dangling PREV_ASSMT_NUM is
        // logged and treated as "no previous month" rather than failing the request.
        OrlLndscpAssmt prev = assmtRepository.findById(prevId).orElse(null);
        if (prev == null) {
            log.warn("PREV_ASSMT_NUM={} of assessment id={} points to a missing assessment",
                    prevId, assmt.getId());
            return Collections.emptyMap();
        }
        // Match only the previous month's own facts (targeted semi-join, not a whole-date scan).
        Map<DimensionKey, FactOrl> prevFacts = factsForAssmt(prevId, prev.getBizDt());
        Set<OrlLndscpAssmtDetails> prevDetails =
                prev.getDetails() != null ? prev.getDetails() : Set.of();
        Map<DimensionKey, NetRiskRating> ratings = new LinkedHashMap<>();
        for (OrlLndscpAssmtDetails d : prevDetails) {
            DimensionKey key = keyOf(d);
            // Previous final rating: the prior overlay when set, else the prior month's calculated rating.
            NetRiskRating rating = d.getOvrlyNetRiskRtng();
            if (rating == null) {
                FactOrl fact = prevFacts.get(key);
                rating = fact != null ? fact.getCalNetRiskRtng() : null;
            }
            ratings.put(key, rating);
        }
        log.debug("Loaded {} previous-month rating(s) from assmt id={}", ratings.size(), prevId);
        return ratings;
    }

    /**
     * The previous month's NRR snapshot for the row matching the given dimension key, or
     * {@code null} when there is no previous assessment or no matching prior detail row. Uses the
     * header projection + a single keyed detail read — the previous assessment's whole detail
     * collection is never loaded.
     */
    private MonthNRRDetails previousMonthDetails(AssmtHeader assmt, DimensionKey key) {
        Long prevId = assmt.prevAssmtNum();
        if (prevId == null) {
            return null;
        }
        AssmtHeader prev = assmtRepository.findHeaderById(prevId).orElse(null);
        if (prev == null) {
            log.warn("PREV_ASSMT_NUM={} of assessment id={} points to a missing assessment",
                    prevId, assmt.id());
            return null;
        }
        OrlLndscpAssmtDetails d = detailsRepository.findByAssmtAndDimension(prevId,
                key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())
                .orElse(null);
        if (d == null) {
            log.debug("No row in previous assessment id={} matches key {}", prevId, key);
            return null;
        }
        LocalDate prevBizDt = prev.bizDt();
        FactOrl prevFact = factFor(prevBizDt, key).orElse(null);
        String prevNrrCalculated =
                NetRiskRating.display(prevFact != null ? prevFact.getCalNetRiskRtng() : null);
        return MonthNRRDetails.builder()
                .id(d.getId())
                .nrrCalculated(prevNrrCalculated)
                .nrr(resolveNrr(d.getOvrlyNetRiskRtng(), prevNrrCalculated))
                .nrrOverlaid(d.getOvrlyNetRiskRtng() != null ? OVERLAID_YES : OVERLAID_NO)
                .overlayJstfkn(d.getOvrlyJstfkn())
                .ctrlEffRtn(prevFact != null ? prevFact.getCtrlEffRtn() : null)
                .assmtPeriod(prev.assmtPeriod())
                .grcMetrics(grcMetricsService.forBizDate(prevBizDt, key))
                .commentry(prevFact != null ? prevFact.getCommentary() : null)
                .build();
    }

    /**
     * Resolves the BU name from the row's own category (drill-down rule):
     * {@code L2/grp_l2 → ORL_BU_NM_L2}, {@code L3/grp_l3 → ORL_BU_NM_L3},
     * {@code L4/grp_l4 → ORL_BU_NM_L4}, {@code loc → "Group"}.
     */
    private String resolveBuByCategory(OrlLndscpAssmtDetails row) {
        LevelCategory category = row.getCategory();
        if (category == null) {
            return null;
        }
        return switch (category) {
            case L2, GRP_L2 -> row.getOrlBuNmL2();
            case L3, GRP_L3 -> row.getOrlBuNmL3();
            case L4, GRP_L4 -> row.getOrlBuNmL4();
            case LOC -> GROUP_LOCATION;
        };
    }

    /** {@code UPDATE_DT_TM} when the assessment has been updated, else {@code CREATE_DT_TM}. */
    private LocalDateTime resolveLastModifiedOn(OrlLndscpAssmt assmt) {
        return assmt.getUpdateDtTm() != null ? assmt.getUpdateDtTm() : assmt.getCreateDtTm();
    }

    /** {@code UPDATED_BY} when the assessment has been updated, else {@code CREATED_BY}. */
    private String resolveLastModifiedBy(OrlLndscpAssmt assmt) {
        return assmt.getUpdatedBy() != null ? assmt.getUpdatedBy() : assmt.getCreatedBy();
    }

    /**
     * Builds the embedded landscape dimensions from the config row and the <em>already-parsed</em>
     * RISK_AREA groups (parsed once by the caller), so the risk-area/cluster views don't re-parse
     * the JSON.
     */
    private LandscapeDimensions toDimensions(OrlLndscpDim dim, List<RiskAreaGroup> riskAreaGroups) {
        Map<String, List<String>> riskAreas = riskAreaParser.riskAreaClusterMap(riskAreaGroups);
        LandscapeBuDetails buDetails = LandscapeBuDetails.builder()
                .lvl(dim.getBizUnitLvl())
                .bizUnits(parseCsv(dim.getBizUnits()))
                .build();

        return LandscapeDimensions.builder()
                .riskAreas(riskAreas.isEmpty() ? null : riskAreas)
                .riskClusters(riskAreaParser.distinctRiskClusters(riskAreaGroups))
                .buDetails(buDetails)
                .locations(parseCsv(dim.getLocations()))
                .build();
    }

    private LandscapeAssmtDetailItem toItem(OrlLndscpAssmtDetails row, Integer bizUnitLvl,
                                            FactOrl currentFact, NetRiskRating prevAssmtFinalNRR,
                                            RiskAreaParser.AreaLookup areaLookup) {
        if (areaLookup == null) {
            log.debug("Risk area '{}' of detail row id={} not found in the landscape config — "
                    + "groupName/riskClusters left empty", row.getRiskArea(), row.getId());
        }
        String nrrCalculated =
                NetRiskRating.display(currentFact != null ? currentFact.getCalNetRiskRtng() : null);
        return LandscapeAssmtDetailItem.builder()
                .id(row.getId())
                .riskArea(row.getRiskArea())
                .groupName(areaLookup != null ? areaLookup.groupName() : null)
                .riskClusters(areaLookup != null ? areaLookup.riskClusters() : List.of())
                .bu(resolveBu(row, bizUnitLvl))
                .location(resolveLocation(row))
                .status(PersistableEnum.dbValue(row.getStatus()))
                .nrrCalculated(nrrCalculated)
                .nrr(resolveNrr(row.getOvrlyNetRiskRtng(), nrrCalculated))
                .riskRatingChange(PersistableEnum.dbValue(currentFact != null ? currentFact.getRiskRtngChge() : null))
                .ctrlEffRtn(currentFact != null ? currentFact.getCtrlEffRtn() : null)
                .commentry(currentFact != null ? currentFact.getCommentary() : null)
                .category(PersistableEnum.dbValue(row.getCategory()))
                .prevAssmtFinalNRR(NetRiskRating.display(prevAssmtFinalNRR))
                .nrrOverlaid(row.getOvrlyNetRiskRtng() != null ? OVERLAID_YES : OVERLAID_NO)
                .build();
    }

    /**
     * Resolves the BU name: {@code "Group"} for the {@code loc} category; otherwise the raw
     * hierarchy column selected by the landscape's BU level (2/3/4). {@code null} for any other level.
     */
    private String resolveBu(OrlLndscpAssmtDetails row, Integer bizUnitLvl) {
        if (LevelCategory.LOC == row.getCategory()) return GROUP_LOCATION;
        if (bizUnitLvl == null) return null;
        return switch (bizUnitLvl) {
            case 2 -> row.getOrlBuNmL2();
            case 3 -> row.getOrlBuNmL3();
            case 4 -> row.getOrlBuNmL4();
            default -> null;
        };
    }

    /**
     * Resolves the display location: the row's own {@code LOCATION} for {@code L2/L3/L4/loc},
     * or {@code "Group"} for the group-level categories {@code grp_l2/grp_l3/grp_l4}.
     */
    private String resolveLocation(OrlLndscpAssmtDetails row) {
        LevelCategory category = row.getCategory();
        if (category == null) return row.getLocation();
        if (DIRECT_LOCATION_CATEGORIES.contains(category)) return row.getLocation();
        if (GROUP_CATEGORIES.contains(category)) return GROUP_LOCATION;
        return row.getLocation();
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
