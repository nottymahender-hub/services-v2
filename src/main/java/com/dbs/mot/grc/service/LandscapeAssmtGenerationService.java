package com.dbs.mot.grc.service;

import com.dbs.mot.grc.enums.AssmtStatus;
import com.dbs.mot.grc.enums.DetailStatus;
import com.dbs.mot.grc.enums.LevelCategory;
import com.dbs.mot.grc.exception.ConflictException;
import com.dbs.mot.grc.exception.NoFactDataException;
import com.dbs.mot.grc.util.RiskAreaParser;
import com.dbs.mot.grc.dto.AssmtGenerationResponse;
import com.dbs.mot.grc.entity.OrlBizUnit;
import com.dbs.mot.grc.entity.OrlLndscpAssmt;
import com.dbs.mot.grc.entity.OrlLndscpAssmtDetails;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.repository.FactOrlRepository;
import com.dbs.mot.grc.repository.OrlBizUnitRepository;
import com.dbs.mot.grc.repository.OrlLndscpAssmtRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
 * Generates a landscape assessment and its thin detail rows for a config, anchored on a caller-supplied
 * as-of date. The assessment reports the month <b>before</b> the as-of date's month (M-1): its
 * {@code ASSEMT_PERIOD} is M-1, its {@code biz_dt} the latest {@code fact_orl.biz_dt} within that month,
 * and it links to the M-2 assessment. When no {@code fact_orl} data exists anywhere for M-1, generation
 * is skipped entirely ({@link NoFactDataException}) rather than falling back to a synthetic date. Rows
 * expand {@code RISK_AREA × business units × locations} into {@code L{lvl}}/{@code grp_l{lvl}}/{@code loc}
 * category rows; the aggregate is saved in one transaction.
 *
 * <p>Detail rows carry only dimensions, category, status and audit columns — <b>no computed values</b>.
 * Calculated ratings, risk-rating change and GRC metrics all live in {@code fact_orl}/module tables and
 * are derived at read time by the assessment read APIs, never at generation. Entry point:
 * {@link #generateForDim(OrlLndscpDim, LocalDate, String)}.
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
    private final FactOrlRepository factRepository;

    /**
     * Generates the assessment for an already-resolved config row. Called per landscape by
     * the bulk generation service — which relies on this method's own transaction so one
     * landscape's failure cannot roll back the others.
     *
     * @param dim    the landscape config to generate from
     * @param asOfDate the as-of date driving the reported period; the assessment reports the month
     *                 <em>before</em> this date's month (M-1) and links to M-2
     * @param userId caller identity, stored as {@code CREATED_BY}
     * @return a summary of the generated assessment
     * @throws ConflictException  if an assessment already exists for this landscape + month
     * @throws NoFactDataException if no {@code fact_orl} data exists anywhere for the reported month
     */
    @Transactional
    public AssmtGenerationResponse generateForDim(OrlLndscpDim dim, LocalDate asOfDate, String userId) {
        Long lndscpNum = dim.getId();
        log.debug("Generating assessment for lndscpNum={} ('{}') asOfDate={} by '{}'",
                lndscpNum, dim.getLndscpNm(), asOfDate, userId);

        // The as-of date's month is M; the assessment reports the previous calendar month (M-1):
        // its period label is M-1 and it links back to the M-2 assessment.
        YearMonth assmtMonth = YearMonth.from(asOfDate).minusMonths(1);
        String assmtPeriod = assmtMonth.format(PERIOD_FMT);
        String priorPeriod = assmtMonth.minusMonths(1).format(PERIOD_FMT);

        // Check for an already-generated period before resolving biz_dt: if it already exists, that's
        // the more relevant skip reason, and there's no need to query fact_orl at all.
        if (assmtRepository.existsByLndscpNumAndPeriod(lndscpNum, assmtPeriod)) {
            throw new ConflictException("An assessment already exists for landscape '"
                    + dim.getLndscpNm() + "' (id " + lndscpNum + ") and period '"
                    + assmtPeriod + "'.");
        }

        LocalDate bizDt = resolveBizDate(assmtMonth);

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
                assmtRepository.findIdByLndscpNumAndPeriod(lndscpNum, priorPeriod);
        log.debug("Previous assessment for lndscpNum={} period='{}': {}",
                lndscpNum, priorPeriod, prevAssmtId.map(String::valueOf).orElse("none"));

        // ── Build the (thin) detail rows ─────────────────────────────────────────
        List<OrlLndscpAssmtDetails> details = new ArrayList<>();
        for (RowSpec spec : expand(riskAreas, bizUnits, locations, lvl)) {
            details.add(buildDetail(spec, lvl, buByName, userId));
        }
        log.debug("Expanded {} detail row(s) for lndscpNum={}", details.size(), lndscpNum);

        // ── Persist the aggregate (root + children) in one transaction ───────────
        OrlLndscpAssmt assmt = OrlLndscpAssmt.builder()
                .lndscpNum(AggregateReference.to(lndscpNum))
                .assmtPeriod(assmtPeriod)
                .bizDt(bizDt)
                .status(AssmtStatus.OPEN)
                .prevAssmtNum(prevAssmtId.map(AggregateReference::<OrlLndscpAssmt, Long>to).orElse(null))
                .createdBy(userId)
                .details(new LinkedHashSet<>(details))
                .build();
        OrlLndscpAssmt saved = assmtRepository.save(assmt);

        log.info("Generated assessment id={} ({} detail rows) for lndscpNum={} period='{}' bizDt={} by '{}'",
                saved.getId(), details.size(), lndscpNum, assmtPeriod, bizDt, userId);

        return AssmtGenerationResponse.builder()
                .lndscpAssmtId(saved.getId())
                .lndscpNm(dim.getLndscpNm())
                .lndscpNum(lndscpNum)
                .assmtPeriod(assmtPeriod)
                .detailRowCount(details.size())
                .build();
    }

    /**
     * Resolves the assessment's business date for the reported month: the latest {@code fact_orl.biz_dt}
     * within that month.
     *
     * @throws NoFactDataException if {@code fact_orl} has no row anywhere within the month — an
     *                             assessment cannot be generated with no underlying data
     */
    private LocalDate resolveBizDate(YearMonth assmtMonth) {
        LocalDate monthStart = assmtMonth.atDay(1);
        LocalDate monthEnd = assmtMonth.atEndOfMonth();
        LocalDate latestInMonth = factRepository.findMaxBizDtBetween(monthStart, monthEnd);
        if (latestInMonth == null) {
            throw new NoFactDataException("No fact_orl data found for period '"
                    + assmtMonth.format(PERIOD_FMT) + "' (" + monthStart + " to " + monthEnd + ").");
        }
        log.debug("Resolved biz_dt={} for period '{}'", latestInMonth, assmtMonth.format(PERIOD_FMT));
        return latestInMonth;
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

    /**
     * Builds one thin detail row from its expanded spec: dimension identity, category and status
     * only. Computed values (calculated NRR, risk-rating change, GRC metrics) are never written here
     * — they are derived at read time from {@code fact_orl}/module tables.
     */
    private OrlLndscpAssmtDetails buildDetail(RowSpec spec, Integer lvl,
                                              Map<String, OrlBizUnit> buByName, String userId) {
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
