package com.dbs.mot.grc.service;

import com.dbs.mot.grc.common.enums.AssmtStatus;
import com.dbs.mot.grc.common.enums.DetailStatus;
import com.dbs.mot.grc.common.enums.LevelCategory;
import com.dbs.mot.grc.common.exception.ConflictException;
import com.dbs.mot.grc.common.util.RiskAreaParser;
import com.dbs.mot.grc.dto.AssmtGenerationResponse;
import com.dbs.mot.grc.entity.OrlBizUnit;
import com.dbs.mot.grc.entity.OrlLndscpAssmt;
import com.dbs.mot.grc.entity.OrlLndscpAssmtDetails;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.repository.OrlBizUnitRepository;
import com.dbs.mot.grc.repository.OrlLndscpAssmtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Generates a landscape assessment and all of its detail rows for a given landscape config.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Reject if an assessment already exists for this landscape + current month — 409.</li>
 *   <li>Expand the config into detail-row specs: {@code RISK_AREA} keys × business units ×
 *       locations, producing {@code L{lvl}}, {@code grp_l{lvl}} and {@code loc} category rows.</li>
 *   <li>Resolve each business unit name to its full (L2, L3, L4) hierarchy path.</li>
 *   <li>Link the new assessment to the previous month's assessment (same
 *       {@code LNDSCP_NUM}, period = current month − 1) via {@code PREV_ASSMT_NUM};
 *       {@code null} when this is the config's first assessment.</li>
 *   <li>Persist the assessment aggregate (root + all thin detail children) in one
 *       transaction.</li>
 * </ol>
 *
 * <p>Detail rows are <b>thin</b>: only dimensions, category, status and audit columns are
 * written. The computed values (calculated NRR, commentary, GRC metrics, etc.) are not
 * stored here — they live in {@code fact_orl} and are matched at read time by the
 * assessment read APIs.
 *
 * <p>The single entry point is {@link #generateForDim(OrlLndscpDim, String)}; the config
 * to generate from is resolved by the caller ({@link BulkAssmtGenerationService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandscapeAssmtGenerationService {

    private static final DateTimeFormatter PERIOD_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final OrlLndscpAssmtRepository assmtRepository;
    private final OrlBizUnitRepository bizUnitRepository;
    private final RiskAreaParser riskAreaParser;

    /**
     * Generates the assessment for an already-resolved config row. Called per landscape by
     * the bulk generation service — which relies on this method's own transaction so one
     * landscape's failure cannot roll back the others.
     *
     * @param dim    the landscape config to generate from
     * @param userId caller identity, stored as {@code CREATED_BY}
     * @return a summary of the generated assessment
     * @throws ConflictException if an assessment already exists for this landscape + month
     */
    @Transactional
    public AssmtGenerationResponse generateForDim(OrlLndscpDim dim, String userId) {
        Long lndscpNum = dim.getId();
        log.debug("Generating assessment for lndscpNum={} ('{}') by '{}'",
                lndscpNum, dim.getLndscpNm(), userId);

        LocalDate today = LocalDate.now();
        String currentPeriod = YearMonth.from(today).format(PERIOD_FMT);
        String previousPeriod = YearMonth.from(today).minusMonths(1).format(PERIOD_FMT);

        if (assmtRepository.existsByLndscpNumAndPeriod(lndscpNum, currentPeriod)) {
            throw new ConflictException("An assessment already exists for landscape '"
                    + dim.getLndscpNm() + "' (id " + lndscpNum + ") and period '"
                    + currentPeriod + "'.");
        }

        // ── Parse config dimensions ──────────────────────────────────────────────
        List<String> riskAreas = riskAreaParser.riskAreaNames(dim.getRiskArea());
        List<String> bizUnits = parseCsv(dim.getBizUnits());
        List<String> locations = parseCsv(dim.getLocations());
        Integer lvl = dim.getBizUnitLvl();
        log.debug("Config: {} risk area(s), {} BU(s), {} location(s), level={}",
                riskAreas.size(), bizUnits.size(), locations.size(), lvl);

        // ── Lookups reused across all rows ───────────────────────────────────────
        Map<String, OrlBizUnit> buByName = loadBuHierarchy(lvl);
        Optional<Long> prevAssmtId =
                assmtRepository.findIdByLndscpNumAndPeriod(lndscpNum, previousPeriod);
        log.debug("Previous assessment for lndscpNum={} period='{}': {}",
                lndscpNum, previousPeriod, prevAssmtId.map(String::valueOf).orElse("none"));

        // ── Build the (thin) detail rows ─────────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();
        List<OrlLndscpAssmtDetails> details = new ArrayList<>();
        for (RowSpec spec : expand(riskAreas, bizUnits, locations, lvl)) {
            details.add(buildDetail(spec, lvl, buByName, userId, now));
        }
        log.debug("Expanded {} detail row(s) for lndscpNum={}", details.size(), lndscpNum);

        // ── Persist the aggregate (root + children) in one transaction ───────────
        OrlLndscpAssmt assmt = OrlLndscpAssmt.builder()
                .lndscpNum(AggregateReference.to(lndscpNum))
                .assmtPeriod(currentPeriod)
                .status(AssmtStatus.OPEN)
                .prevAssmtNum(prevAssmtId.map(AggregateReference::<OrlLndscpAssmt, Long>to).orElse(null))
                .createdBy(userId)
                .createDtTm(now)
                .details(new LinkedHashSet<>(details))
                .build();
        OrlLndscpAssmt saved = assmtRepository.save(assmt);

        log.info("Generated assessment id={} ({} detail rows) for lndscpNum={} period='{}' by '{}'",
                saved.getId(), details.size(), lndscpNum, currentPeriod, userId);

        return AssmtGenerationResponse.builder()
                .lndscpAssmtId(saved.getId())
                .lndscpNm(dim.getLndscpNm())
                .lndscpNum(lndscpNum)
                .assmtPeriod(currentPeriod)
                .detailRowCount(details.size())
                .build();
    }

    // ── Expansion ────────────────────────────────────────────────────────────────

    /**
     * Expands the config into row specs. For each risk area:
     * <ul>
     *   <li>{@code L{lvl}}     — every (business unit × location) pair,</li>
     *   <li>{@code grp_l{lvl}} — every business unit, with no location,</li>
     *   <li>{@code loc}        — every location, with no business unit.</li>
     * </ul>
     */
    private List<RowSpec> expand(List<String> riskAreas, List<String> bizUnits,
                                 List<String> locations, Integer lvl) {
        LevelCategory levelCategory = LevelCategory.fromDbValue("L" + lvl);
        LevelCategory groupCategory = LevelCategory.fromDbValue("grp_l" + lvl);

        List<RowSpec> specs = new ArrayList<>();
        for (String riskArea : riskAreas) {
            for (String bu : bizUnits) {
                for (String location : locations) {
                    specs.add(new RowSpec(riskArea, bu, location, levelCategory));
                }
            }
            for (String bu : bizUnits) {
                specs.add(new RowSpec(riskArea, bu, null, groupCategory));
            }
            for (String location : locations) {
                specs.add(new RowSpec(riskArea, null, location, LevelCategory.LOC));
            }
        }
        return specs;
    }

    // ── Per-row build ──────────────────────────────────────────────────────────

    private OrlLndscpAssmtDetails buildDetail(RowSpec spec, Integer lvl,
                                              Map<String, OrlBizUnit> buByName,
                                              String userId, LocalDateTime now) {
        // Resolve the BU hierarchy columns (null for 'loc' rows which carry no BU).
        String l2 = null;
        String l3 = null;
        String l4 = null;
        if (spec.orlBu() != null) {
            OrlBizUnit bu = buByName.get(spec.orlBu());
            if (bu != null) {
                l2 = bu.getOrlBuNmL2();
                l3 = bu.getOrlBuNmL3();
                l4 = bu.getOrlBuNmL4();
            } else {
                // Unresolved BU: keep the name at its own level, leave the rest null,
                // and warn — one bad reference should not fail the whole generation.
                log.warn("Business unit '{}' not found at LVL_OF_HIER={} — leaving higher/lower levels null",
                        spec.orlBu(), lvl);
                l2 = (lvl == 2) ? spec.orlBu() : null;
                l3 = (lvl == 3) ? spec.orlBu() : null;
                l4 = (lvl == 4) ? spec.orlBu() : null;
            }
        }

        // Thin row — computed values are matched from fact_orl at read time.
        // Empty dimension columns are stored as '' (never null) so the unique index
        // on (assessment, risk area, BU path, location) actually enforces uniqueness.
        return OrlLndscpAssmtDetails.builder()
                .riskArea(spec.riskArea())
                .orlBuNmL2(emptyIfNull(l2))
                .orlBuNmL3(emptyIfNull(l3))
                .orlBuNmL4(emptyIfNull(l4))
                .location(emptyIfNull(spec.location()))
                .category(spec.category())
                .status(DetailStatus.OPEN)
                .createdBy(userId)
                .createDtTm(now)
                .build();
    }

    /** Empty dimension columns are persisted as '' (never null) to keep the unique index effective. */
    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    // ── Lookups ────────────────────────────────────────────────────────────────

    /**
     * Loads all business units at the landscape's level, keyed by the name column for
     * that level (which is what the generated {@code ORL_BU} values are matched against).
     */
    private Map<String, OrlBizUnit> loadBuHierarchy(Integer lvl) {
        if (lvl == null) {
            return Collections.emptyMap();
        }
        Function<OrlBizUnit, String> nameAtLevel = switch (lvl) {
            case 2 -> OrlBizUnit::getOrlBuNmL2;
            case 3 -> OrlBizUnit::getOrlBuNmL3;
            case 4 -> OrlBizUnit::getOrlBuNmL4;
            default -> null;
        };
        if (nameAtLevel == null) {
            log.warn("Unsupported BIZ_UNIT_LVL={} — no business unit hierarchy loaded", lvl);
            return Collections.emptyMap();
        }
        Map<String, OrlBizUnit> map = new LinkedHashMap<>();
        for (OrlBizUnit bu : bizUnitRepository.findByLvlOfHier(lvl)) {
            String name = nameAtLevel.apply(bu);
            if (name != null) {
                map.putIfAbsent(name, bu);
            }
        }
        return map;
    }

    // ── Parsing helpers ────────────────────────────────────────────────────────

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    // ── Internal value types ─────────────────────────────────────────────────────

    /** One expanded CSV row before BU-hierarchy resolution. */
    private record RowSpec(String riskArea, String orlBu, String location, LevelCategory category) {}
}
