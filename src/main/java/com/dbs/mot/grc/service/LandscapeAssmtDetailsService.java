package com.dbs.mot.grc.service;

import com.dbs.mot.grc.enums.DetailStatus;
import com.dbs.mot.grc.enums.LevelCategory;
import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.enums.PersistableEnum;
import com.dbs.mot.grc.enums.RiskRatingChange;
import com.dbs.mot.grc.exception.ConflictException;
import com.dbs.mot.grc.exception.NotFoundException;
import com.dbs.mot.grc.util.RiskAreaParser;
import com.dbs.mot.grc.util.RiskRatingChanges;
import com.dbs.mot.grc.util.SgtDateTimes;
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
import com.dbs.mot.grc.dto.OverlayResponse;
import com.dbs.mot.grc.dto.RiskAreaGroup;
import com.dbs.mot.grc.dto.SaveAssmtDetailOverlayNRRRequest;
import com.dbs.mot.grc.entity.FactOrl;
import com.dbs.mot.grc.entity.ModuleFact;
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
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read logic for the landscape-assessment APIs (list, drill-down, overlay + commentary save).
 *
 * <p>Detail rows are thin; computed values live in {@code fact_orl}, matched at read time by
 * dimension key ({@code RISK_AREA, ORL_BU_NM_L2..L4, LOCATION}). The list uses a DB-side semi-join
 * ({@code FactOrlRepository.findMatchingByAssmtDetails}) to fetch only the facts it needs; the
 * drill-down fetches each fact by (biz date, dimension key). Enum columns are returned as their DB value.
 *
 * <p><b>Risk-rating change is always derived, never stored.</b> An assessment detail row's overall
 * change and each module's change are computed on every read by comparing two net risk ratings /
 * module snapshots via {@link RiskRatingChanges#derive}. This keeps the value always accurate (e.g.
 * if an earlier assessment's overlay is edited later) and needs no write-time bookkeeping.
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
                .map(row -> toItem(row, dim.getBizUnitLvl(), new RowEnrichment(
                        currentFacts.get(keyOf(row)), prevFinalRatings.get(keyOf(row)),
                        riskAreaLookup.get(row.getRiskArea()))))
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
     * Returns the drill-down view of one detail row: its current-month, previous-month and live NRR
     * snapshots. Every risk-rating change (overall and per-module) is computed fresh — nothing is
     * read from a stored change column.
     *
     * <p>The algorithm is a straight sequence of four comparisons, each one a "fetch a snapshot,
     * compare it against a baseline snapshot, build a result" step:
     * <ol>
     *   <li><b>Current</b> — this assessment's own snapshot (business date, fact, module facts).</li>
     *   <li><b>Previous</b> — one assessment back's snapshot, plus its own detail row for this
     *       dimension. Comparing <i>current vs. previous</i> builds {@code currentMonthNRRDetails}.</li>
     *   <li><b>Previous-to-previous</b> — two assessments back's snapshot. Comparing
     *       <i>previous vs. previous-to-previous</i> builds {@code prevMonthNRRDetails} (the previous
     *       block's own change, computed the same way one period earlier).</li>
     *   <li><b>Live</b> — the latest snapshot across {@code fact_orl}. Comparing <i>live vs.
     *       current</i> builds {@code liveNRRDetails}.</li>
     * </ol>
     * Each period's snapshot (fact + module facts) is fetched exactly once and reused everywhere it
     * is needed — e.g. the previous period's snapshot serves both as the baseline for the current
     * block and as the target for the previous block, so nothing is fetched twice.
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

        // a. Current: this assessment's own business date, fact and module facts.
        Snapshot current = snapshotAt(assmt.bizDt(), key);

        // b. Previous: one assessment back. Its detail row is needed twice — as the baseline for the
        //    current block's change, and to build the previous block itself — so it is loaded once
        //    here and reused both times.
        AssmtHeader prevAssmt = previousAssmtHeader(assmt);
        OrlLndscpAssmtDetails prevRow = detailRowFor(prevAssmt, key);
        Snapshot previous = snapshotAt(bizDtOf(prevAssmt), key);

        // b.i. Compare current against previous → currentMonthNRRDetails.
        MonthNRRDetails currentDetails = buildMonthDetails(
                new PeriodView(row, current), new PeriodView(prevRow, previous), assmt.assmtPeriod())
                .revisedCommentary(row.getRevisedCommentary())
                .build();

        // c. Previous-to-previous: two assessments back — the baseline for the previous block's own
        //    change. Only ever needed once, so it is fetched right where it is used.
        AssmtHeader prevPrevAssmt = previousAssmtHeader(prevAssmt);
        OrlLndscpAssmtDetails prevPrevRow = detailRowFor(prevPrevAssmt, key);
        Snapshot prevPrevious = snapshotAt(bizDtOf(prevPrevAssmt), key);

        // c.i. Compare previous against previous-to-previous → prevMonthNRRDetails.
        //      null when there is no previous assessment, or it has no row for this dimension.
        MonthNRRDetails previousDetails = prevRow == null ? null
                : buildMonthDetails(
                        new PeriodView(prevRow, previous), new PeriodView(prevPrevRow, prevPrevious), prevAssmt.assmtPeriod())
                        .id(prevRow.getId())
                        .build();

        // d. Live: the latest business date across fact_orl. Reuse the current snapshot when they
        //    coincide (the common case) instead of fetching it again.
        LocalDate maxBizDt = factRepository.findMaxBizDt();
        Snapshot live = Objects.equals(maxBizDt, current.bizDt()) ? current : snapshotAt(maxBizDt, key);

        // d.i. Compare live against current → liveNRRDetails.
        LiveNRRDetails liveDetails = buildLiveDetails(live, current);

        log.info("Returning detail id={} of assessment id={} (prev {}, live {})",
                assmtDetailId, lndscpAssmtId,
                previousDetails != null ? "found" : "absent", liveDetails != null ? "found" : "absent");

        return AssmtDetailResponse.builder()
                .id(row.getId())
                .landscapeAssessmentId(assmt.id())
                .landscapeId(assmt.lndscpNum())
                .riskArea(row.getRiskArea())
                .bu(resolveBuByCategory(row))
                .location(resolveLocation(row))
                .status(PersistableEnum.dbValue(row.getStatus()))
                .lastModifiedOn(SgtDateTimes.toSgt(row.getUpdateDtTm() != null ? row.getUpdateDtTm() : row.getCreateDtTm()))
                .lastModifiedBy(row.getUpdatedBy() != null ? row.getUpdatedBy() : row.getCreatedBy())
                .lastRefreshed(maxBizDt)
                .currentMonthNRRDetails(currentDetails)
                .prevMonthNRRDetails(previousDetails)
                .liveNRRDetails(liveDetails)
                .category(PersistableEnum.dbValue(row.getCategory()))
                .commentaryRevisedBy(row.getCommentaryRevisedBy())
                .commentaryRevisedAt(SgtDateTimes.toSgt(row.getCommentaryRevisedAt()))
                .build();
    }

    /**
     * Saves the analyst overlay (overlaid net risk rating, its justification, and the revised
     * commentary) onto an assessment detail row in one call, and stamps the auditor.
     *
     * <p>{@code COMMENTARY_REVISED_BY}/{@code COMMENTARY_REVISED_AT} are stamped only when the
     * incoming commentary actually differs from what is already stored — an overlay-only save that
     * resends the same commentary text leaves the revision audit trail untouched.
     *
     * <p>The returned risk-rating change is freshly derived (previous assessment's final NRR for
     * this dimension vs. this detail's effective NRR after the save) — it is not persisted.
     *
     * @throws NotFoundException if the assessment or the detail does not exist
     * @throws ConflictException if the detail is not in {@code Open} status
     */
    @Transactional
    public OverlayResponse saveOverlay(Long lndscpAssmtId, Long assmtDetailId,
                                       SaveAssmtDetailOverlayNRRRequest request, String username) {
        log.debug("Saving overlay for detail id={} of assessment id={} by '{}'",
                assmtDetailId, lndscpAssmtId, username);

        // Header projection: existence (404) + biz_dt / PREV_ASSMT_NUM used by the change derivation.
        AssmtHeader assmt = assmtRepository.findHeaderById(lndscpAssmtId)
                .orElseThrow(() -> new NotFoundException(
                        "Landscape assessment not found for id: " + lndscpAssmtId));

        OrlLndscpAssmtDetails detail = requireOpenDetail(assmtDetailId);
        DimensionKey key = keyOf(detail);

        String overlaidNrr = blankToNull(request.getOverlaidNRR());
        NetRiskRating newOverlay = overlaidNrr != null ? NetRiskRating.fromDbValue(overlaidNrr) : null;
        boolean overlayChanged = !Objects.equals(newOverlay, detail.getOvrlyNetRiskRtng());

        String newCommentary = blankToNull(request.getRevisedCommentary());
        boolean commentaryChanged = !Objects.equals(newCommentary, detail.getRevisedCommentary());

        // The fact for this dimension on the assessment's own business date is needed either way
        // now: as the calculated-rating fallback (when no overlay is set) and for CTRL_EFF_RTN in
        // the response — so it is fetched once, unconditionally, and reused for both.
        Optional<FactOrl> fact = factFor(assmt.bizDt(), key);

        OrlLndscpAssmtDetails.OrlLndscpAssmtDetailsBuilder toSave = detail.toBuilder()
                .ovrlyNetRiskRtng(newOverlay)
                .ovrlyJstfkn(blankToNull(request.getOverlayJstfkn()))
                .revisedCommentary(newCommentary)
                .updatedBy(username);
        if (commentaryChanged) {
            toSave.commentaryRevisedBy(username).commentaryRevisedAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        OrlLndscpAssmtDetails saved = detailsRepository.save(toSave.build());

        // Derive the change fresh from the just-saved overlay — never persisted.
        NetRiskRating previousFinal = finalRatingForDimension(previousAssmtHeader(assmt), key);
        NetRiskRating currentCalculated = fact.map(FactOrl::getCalNetRiskRtng).orElse(null);
        NetRiskRating currentEffective = effectiveNrr(newOverlay, currentCalculated);
        RiskRatingChange riskRtngChge = RiskRatingChanges.derive(previousFinal, currentEffective);

        log.info("Saved overlay for detail id={} of assessment id={} by '{}' "
                        + "(overlayChanged={}, commentaryChanged={}) -> {}",
                assmtDetailId, lndscpAssmtId, username, overlayChanged, commentaryChanged, riskRtngChge);

        return OverlayResponse.builder()
                .lndscpAssmtId(lndscpAssmtId)
                .assmtDetailId(assmtDetailId)
                .overlaidNRR(PersistableEnum.dbValue(saved.getOvrlyNetRiskRtng()))
                .overlayJstfkn(saved.getOvrlyJstfkn())
                .nrrOverlaid(saved.getOvrlyNetRiskRtng() != null ? OVERLAID_YES : OVERLAID_NO)
                .ctrlEffRtn(fact.map(FactOrl::getCtrlEffRtn).orElse(null))
                .status(PersistableEnum.dbValue(saved.getStatus()))
                .revisedCommentary(saved.getRevisedCommentary())
                .commentaryRevisedBy(saved.getCommentaryRevisedBy())
                .commentaryRevisedAt(SgtDateTimes.toSgt(saved.getCommentaryRevisedAt()))
                .riskRatingChange(PersistableEnum.dbValue(riskRtngChge))
                .build();
    }

    /** Loads a detail row by id (404 if absent) and requires it to be in {@code Open} status (409). */
    private OrlLndscpAssmtDetails requireOpenDetail(Long assmtDetailId) {
        OrlLndscpAssmtDetails detail = detailsRepository.findById(assmtDetailId)
                .orElseThrow(() -> new NotFoundException(
                        "Assessment detail not found for id: " + assmtDetailId));
        if (detail.getStatus() != DetailStatus.OPEN) {
            throw new ConflictException("Save is not allowed: assessment detail " + assmtDetailId
                    + " is not in '" + DetailStatus.OPEN.getDbValue() + "' status (current: "
                    + PersistableEnum.dbValue(detail.getStatus()) + ").");
        }
        return detail;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    // ── Drill-down: one business date's snapshot + its comparisons ─────────────────────────────

    /**
     * One business date's snapshot for a dimension: its {@code fact_orl} row and its module facts.
     * Every period in the drill-down (current/previous/previous-to-previous/live) is exactly one of
     * these, fetched once and compared against another {@code Snapshot} as the baseline.
     */
    private record Snapshot(LocalDate bizDt, FactOrl fact, Map<String, ModuleFact> moduleFacts) {
    }

    /**
     * Loads the snapshot for a business date + dimension. Safe to call with a {@code null} date (e.g.
     * "no previous assessment") — returns an empty snapshot with no database access at all.
     */
    private Snapshot snapshotAt(LocalDate bizDt, DimensionKey key) {
        FactOrl fact = factFor(bizDt, key).orElse(null);
        Map<String, ModuleFact> moduleFacts = grcMetricsService.moduleFacts(bizDt, key);
        return new Snapshot(bizDt, fact, moduleFacts);
    }

    /**
     * The detail row for one dimension within a given assessment, or {@code null} when the assessment
     * is {@code null} or has no matching row.
     */
    private OrlLndscpAssmtDetails detailRowFor(AssmtHeader assmt, DimensionKey key) {
        if (assmt == null) {
            return null;
        }
        return detailsRepository.findByAssmtAndDimension(assmt.id(),
                key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())
                .orElse(null);
    }

    /**
     * One period's detail row together with its {@link Snapshot} — every drill-down comparison
     * needs both as a unit (the row for its overlay/status, the snapshot for its fact/module data),
     * so they travel together instead of as separate parameters.
     */
    private record PeriodView(OrlLndscpAssmtDetails row, Snapshot snapshot) {}

    /**
     * Builds one month's block (used for both the current and previous drill-down blocks) by
     * comparing {@code target}'s period against the {@code baseline} period. Returns a builder —
     * rather than the built object — so each call site can still set its own block-specific field
     * ({@code id} for the previous block, {@code revisedCommentary} for the current block) before
     * calling {@code .build()}.
     *
     * @param target      this block's own detail row + snapshot
     * @param baseline    the earlier assessment's matching detail row + snapshot (the comparison baseline)
     * @param assmtPeriod the reported period label for this block
     */
    private MonthNRRDetails.MonthNRRDetailsBuilder buildMonthDetails(
            PeriodView target, PeriodView baseline, String assmtPeriod) {

        OrlLndscpAssmtDetails row = target.row();
        NetRiskRating calculated = target.snapshot().fact() != null ? target.snapshot().fact().getCalNetRiskRtng() : null;
        NetRiskRating effective = effectiveNrr(row.getOvrlyNetRiskRtng(), calculated);
        NetRiskRating baselineFinal = finalRatingFrom(baseline.row(), baseline.snapshot());
        RiskRatingChange change = RiskRatingChanges.derive(baselineFinal, effective);

        String nrrCalculated = PersistableEnum.dbValue(calculated);
        return MonthNRRDetails.builder()
                .nrrCalculated(nrrCalculated)
                .nrr(resolveNrr(row.getOvrlyNetRiskRtng(), nrrCalculated))
                .nrrOverlaid(row.getOvrlyNetRiskRtng() != null ? OVERLAID_YES : OVERLAID_NO)
                .overlayJstfkn(row.getOvrlyJstfkn())
                .ctrlEffRtn(target.snapshot().fact() != null ? target.snapshot().fact().getCtrlEffRtn() : null)
                .assmtPeriod(assmtPeriod)
                .riskRatingChange(PersistableEnum.dbValue(change))
                .grcMetrics(grcMetricsService.buildBlocks(target.snapshot().moduleFacts(), baseline.snapshot().moduleFacts()))
                .commentry(target.snapshot().fact() != null ? target.snapshot().fact().getCommentary() : null)
                .commentaryRevisedBy(row.getCommentaryRevisedBy())
                .commentaryRevisedAt(SgtDateTimes.toSgt(row.getCommentaryRevisedAt()));
    }

    /**
     * Builds the live block by comparing the live snapshot against the current assessment's snapshot
     * as the baseline. {@code null} when there is no live fact for this dimension.
     */
    private LiveNRRDetails buildLiveDetails(Snapshot live, Snapshot current) {
        if (live.fact() == null) {
            log.debug("No live fact_orl row for this dimension on biz_dt={}", live.bizDt());
            return null;
        }
        return LiveNRRDetails.builder()
                .nrr(PersistableEnum.dbValue(live.fact().getCalNetRiskRtng()))
                .nrrOverlaid(OVERLAID_NO)
                .overlayJstfkn(null)
                .lastRefreshed(live.bizDt())
                .ctrlEffRtn(live.fact().getCtrlEffRtn())
                .grcMetrics(grcMetricsService.buildBlocks(live.moduleFacts(), current.moduleFacts()))
                .commentry(live.fact().getCommentary())
                .build();
    }

    // ── Risk-rating-change derivation (shared by list, drill-down and overlay save) ──────────────

    /**
     * The effective net risk rating for a dimension: the analyst overlay when set, otherwise the
     * calculated rating. This is the enum counterpart of {@link #resolveNrr} (which formats the same
     * choice as a display string) — used wherever a rating needs to be *compared*, not displayed.
     */
    private NetRiskRating effectiveNrr(NetRiskRating overlay, NetRiskRating calculated) {
        return overlay != null ? overlay : calculated;
    }

    /**
     * The final net risk rating for a dimension, computed from an <em>already-loaded</em> detail row
     * and its snapshot (no database access): the row's overlay when set, else the snapshot fact's
     * calculated rating. {@code null} when the row itself is {@code null} (no matching detail row).
     *
     * <p>Used by the drill-down, which always loads a full {@link Snapshot} per period anyway (for
     * the GRC metric blocks) — so deriving the final rating from it is free. Contrast with
     * {@link #finalRatingForDimension}, used where no snapshot is otherwise needed.
     */
    private NetRiskRating finalRatingFrom(OrlLndscpAssmtDetails detail, Snapshot snapshot) {
        if (detail == null) {
            return null;
        }
        NetRiskRating calculated = snapshot.fact() != null ? snapshot.fact().getCalNetRiskRtng() : null;
        return effectiveNrr(detail.getOvrlyNetRiskRtng(), calculated);
    }

    /**
     * The final net risk rating for one dimension within a given assessment, fetching its own detail
     * row and (only if needed) its {@code fact_orl} row. Used by the overlay save, which has no other
     * use for a full module-fact {@link Snapshot} and would otherwise pay for one needlessly.
     *
     * @return {@code null} when the assessment is {@code null} or has no matching detail row
     */
    private NetRiskRating finalRatingForDimension(AssmtHeader assmt, DimensionKey key) {
        if (assmt == null) {
            return null;
        }
        OrlLndscpAssmtDetails detail = detailRowFor(assmt, key);
        if (detail == null) {
            return null;
        }
        if (detail.getOvrlyNetRiskRtng() != null) {
            return detail.getOvrlyNetRiskRtng();
        }
        return factFor(assmt.bizDt(), key).map(FactOrl::getCalNetRiskRtng).orElse(null);
    }

    /**
     * Loads the header of the assessment that {@code referencing} points to via {@code PREV_ASSMT_NUM},
     * or {@code null} when {@code referencing} is {@code null}, has no previous assessment, or the
     * reference is dangling (logged as a warning; treated as "no such assessment" rather than failing
     * the request). Composable: calling it again on the result walks one period further back.
     */
    private AssmtHeader previousAssmtHeader(AssmtHeader referencing) {
        if (referencing == null || referencing.prevAssmtNum() == null) {
            return null;
        }
        AssmtHeader header = assmtRepository.findHeaderById(referencing.prevAssmtNum()).orElse(null);
        if (header == null) {
            log.warn("PREV_ASSMT_NUM={} referenced by assessment id={} points to a missing assessment",
                    referencing.prevAssmtNum(), referencing.id());
        }
        return header;
    }

    /** The business date of an assessment header, or {@code null} when the header itself is {@code null}. */
    private LocalDate bizDtOf(AssmtHeader assmt) {
        return assmt != null ? assmt.bizDt() : null;
    }

    /**
     * The net risk rating to display: the analyst overlay when set, otherwise the calculated
     * rating. Shared by the list items and the drill-down's current/previous-month blocks.
     */
    private String resolveNrr(NetRiskRating overlay, String nrrCalculated) {
        return overlay != null ? PersistableEnum.dbValue(overlay) : nrrCalculated;
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
        Set<OrlLndscpAssmtDetails> prevDetails =
                prev.getDetails() != null ? prev.getDetails() : Set.of();

        // The fact fallback is only ever used for a row with no overlay — skip the semi-join
        // fetch entirely when every previous-month row already carries an overlay.
        boolean anyRowNeedsFact = prevDetails.stream().anyMatch(d -> d.getOvrlyNetRiskRtng() == null);
        Map<DimensionKey, FactOrl> prevFacts =
                anyRowNeedsFact ? factsForAssmt(prevId, prev.getBizDt()) : Collections.emptyMap();
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

    /**
     * {@code UPDATE_DT_TM} when the assessment has been updated, else {@code CREATE_DT_TM},
     * converted from the stored UTC to Singapore time for the response.
     */
    private LocalDateTime resolveLastModifiedOn(OrlLndscpAssmt assmt) {
        return SgtDateTimes.toSgt(assmt.getUpdateDtTm() != null ? assmt.getUpdateDtTm() : assmt.getCreateDtTm());
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

    /**
     * A row's per-dimension enrichment, looked up once per row before building its item: the
     * current fact, the previous assessment's final rating for this dimension, and the risk-area
     * group/cluster lookup. Bundled together since all three are always looked up as a unit.
     */
    private record RowEnrichment(
            FactOrl currentFact, NetRiskRating prevAssmtFinalNRR, RiskAreaParser.AreaLookup areaLookup) {}

    private LandscapeAssmtDetailItem toItem(OrlLndscpAssmtDetails row, Integer bizUnitLvl, RowEnrichment enrichment) {
        FactOrl currentFact = enrichment.currentFact();
        NetRiskRating prevAssmtFinalNRR = enrichment.prevAssmtFinalNRR();
        RiskAreaParser.AreaLookup areaLookup = enrichment.areaLookup();
        if (areaLookup == null) {
            log.debug("Risk area '{}' of detail row id={} not found in the landscape config — "
                    + "groupName/riskClusters left empty", row.getRiskArea(), row.getId());
        }
        NetRiskRating currentCalculated = currentFact != null ? currentFact.getCalNetRiskRtng() : null;
        String nrrCalculated = PersistableEnum.dbValue(currentCalculated);
        NetRiskRating currentEffective = effectiveNrr(row.getOvrlyNetRiskRtng(), currentCalculated);
        RiskRatingChange riskRatingChange = RiskRatingChanges.derive(prevAssmtFinalNRR, currentEffective);
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
                .riskRatingChange(PersistableEnum.dbValue(riskRatingChange))
                .ctrlEffRtn(currentFact != null ? currentFact.getCtrlEffRtn() : null)
                .commentry(StringUtils.hasText(row.getRevisedCommentary()) ? row.getRevisedCommentary() : currentFact != null ? currentFact.getCommentary() : null)
                .category(PersistableEnum.dbValue(row.getCategory()))
                .prevAssmtFinalNRR(PersistableEnum.dbValue(prevAssmtFinalNRR))
                .nrrOverlaid(row.getOvrlyNetRiskRtng() != null ? OVERLAID_YES : OVERLAID_NO)
                .overlayJstfkn(row.getOvrlyJstfkn())
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
