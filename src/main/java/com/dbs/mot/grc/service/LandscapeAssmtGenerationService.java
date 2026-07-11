package com.dbs.mot.grc.service;

import com.dbs.mot.grc.common.exception.ConflictException;
import com.dbs.mot.grc.common.exception.NotFoundException;
import com.dbs.mot.grc.dto.AssmtGenerationResponse;
import com.dbs.mot.grc.dto.DerivedDetailColumns;
import com.dbs.mot.grc.dto.DimensionKey;
import com.dbs.mot.grc.dto.MatchedFactRows;
import com.dbs.mot.grc.entity.InaFactOrl;
import com.dbs.mot.grc.entity.IncFactOrl;
import com.dbs.mot.grc.entity.KriFactOrl;
import com.dbs.mot.grc.entity.OrlBizUnit;
import com.dbs.mot.grc.entity.OrlLndscpAssmt;
import com.dbs.mot.grc.entity.OrlLndscpAssmtDetails;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.entity.RcsaFactOrl;
import com.dbs.mot.grc.repository.InaFactOrlRepository;
import com.dbs.mot.grc.repository.IncFactOrlRepository;
import com.dbs.mot.grc.repository.KriFactOrlRepository;
import com.dbs.mot.grc.repository.OrlBizUnitRepository;
import com.dbs.mot.grc.repository.OrlLndscpAssmtRepository;
import com.dbs.mot.grc.repository.OrlLndscpDimRepository;
import com.dbs.mot.grc.repository.RcsaFactOrlRepository;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Generates a landscape assessment and all of its detail rows for a given landscape.
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Resolve the landscape config by name: only rows that are {@code ACTIVE} and whose
 *       {@code EFFECT_START_DT}..{@code EFFECT_END_DT} window contains today qualify —
 *       404 when none qualify, 409 when more than one does (ambiguous config).</li>
 *   <li>Reject if an assessment already exists for this landscape + current month — 409.</li>
 *   <li>Expand the config into detail-row specs: {@code RISK_AREA} keys × business units ×
 *       locations, producing {@code L{lvl}}, {@code grp_l{lvl}} and {@code loc} category rows.</li>
 *   <li>Resolve each business unit name to its full (L2, L3, L4) hierarchy path.</li>
 *   <li>Link the new assessment to the previous month's assessment (same
 *       {@code LNDSCP_NUM}, period = current month − 1) via {@code PREV_ASSMT_NUM};
 *       {@code null} when this is the config's first assessment.</li>
 *   <li>Match each row to the module fact rows (by dimension key, for today's {@code biz_dt})
 *       and derive {@code GRC_METRICS}, {@code CAL_NET_RISK_RTNG}, etc. via
 *       {@link FactDerivationService}.</li>
 *   <li>Persist the assessment aggregate (root + all detail children) in one transaction.</li>
 * </ol>
 *
 * <p>All derivation lives here / in {@link FactDerivationService}; the repositories run
 * only plain, single-table queries with no transformations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandscapeAssmtGenerationService {

    private static final DateTimeFormatter PERIOD_FMT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final String STATUS_OPEN = "Open";
    private static final String CATEGORY_LOC = "loc";

    /** Shared mapper with duplicate-key detection for parsing the RISK_AREA JSON. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final OrlLndscpDimRepository dimRepository;
    private final OrlLndscpAssmtRepository assmtRepository;
    private final OrlBizUnitRepository bizUnitRepository;
    private final IncFactOrlRepository incFactRepository;
    private final InaFactOrlRepository inaFactRepository;
    private final KriFactOrlRepository kriFactRepository;
    private final RcsaFactOrlRepository rcsaFactRepository;
    private final FactDerivationService factDerivationService;

    /**
     * Generates the assessment for the landscape with the given name.
     *
     * <p>The config row is resolved from {@code orl_lndscp_dim}: only rows with
     * {@code STATUS = 'ACTIVE'} whose effective window contains today qualify.
     *
     * @param lndscpNm landscape name ({@code orl_lndscp_dim.LNDSCP_NM}), from the path
     * @param userId   caller identity, stored as {@code CREATED_BY}
     * @return a summary of the generated assessment
     * @throws NotFoundException if no active config is effective today for {@code lndscpNm}
     * @throws ConflictException if more than one active config is effective today
     *                           (ambiguous), or an assessment already exists for this
     *                           landscape + current month
     */
    @Transactional
    public AssmtGenerationResponse generateByName(String lndscpNm, String userId) {
        log.debug("Generating assessment for landscape '{}' by '{}'", lndscpNm, userId);
        OrlLndscpDim dim = resolveEffectiveConfig(lndscpNm);
        return generateForDim(dim, userId);
    }

    /**
     * Resolves the single ACTIVE, currently-effective config row for the given landscape
     * name. All filtering happens here in the service layer — the repository query is a
     * plain single-table lookup by name.
     */
    private OrlLndscpDim resolveEffectiveConfig(String lndscpNm) {
        LocalDate today = LocalDate.now();
        List<OrlLndscpDim> effective = dimRepository.findByLndscpNm(lndscpNm).stream()
                .filter(dim -> dim.isActiveAndEffectiveOn(today))
                .toList();

        if (effective.isEmpty()) {
            throw new NotFoundException(
                    "No active landscape config effective today for '" + lndscpNm + "'.");
        }
        if (effective.size() > 1) {
            String ids = effective.stream()
                    .map(dim -> String.valueOf(dim.getId()))
                    .collect(Collectors.joining(", "));
            throw new ConflictException("Multiple active configs (ids: " + ids
                    + ") are effective today for landscape '" + lndscpNm
                    + "' — cannot determine which one to generate from.");
        }
        log.debug("Resolved landscape '{}' to config id={} (version={})",
                lndscpNm, effective.get(0).getId(), effective.get(0).getVersion());
        return effective.get(0);
    }

    /**
     * Generates the assessment for an already-resolved config row. Called directly by
     * {@link #generateByName} and, per landscape, by the bulk generation service — which
     * relies on this method's own transaction so one landscape's failure cannot roll
     * back the others.
     *
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
        List<String> riskAreas = parseRiskAreaKeys(dim.getRiskArea());
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
        FactIndex facts = loadFacts(today);

        // ── Build the detail rows ────────────────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();
        List<OrlLndscpAssmtDetails> details = new ArrayList<>();
        for (RowSpec spec : expand(riskAreas, bizUnits, locations, lvl)) {
            details.add(buildDetail(spec, lvl, buByName, facts, userId, now));
        }
        log.debug("Expanded {} detail row(s) for lndscpNum={}", details.size(), lndscpNum);

        // ── Persist the aggregate (root + children) in one transaction ───────────
        OrlLndscpAssmt assmt = OrlLndscpAssmt.builder()
                .lndscpNum(AggregateReference.to(lndscpNum))
                .assmtPeriod(currentPeriod)
                .status(STATUS_OPEN)
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
        String levelCategory = "L" + lvl;
        String groupCategory = "grp_l" + lvl;

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
                specs.add(new RowSpec(riskArea, null, location, CATEGORY_LOC));
            }
        }
        return specs;
    }

    // ── Per-row build ──────────────────────────────────────────────────────────

    private OrlLndscpAssmtDetails buildDetail(RowSpec spec, Integer lvl,
                                              Map<String, OrlBizUnit> buByName,
                                              FactIndex facts, String userId, LocalDateTime now) {
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

        DimensionKey key = new DimensionKey(spec.riskArea(), l2, l3, l4, spec.location(), spec.category());

        DerivedDetailColumns derived = factDerivationService.derive(facts.match(key));

        return OrlLndscpAssmtDetails.builder()
                .riskArea(spec.riskArea())
                .orlBuNmL2(l2)
                .orlBuNmL3(l3)
                .orlBuNmL4(l4)
                .location(spec.location())
                .category(spec.category())
                .calNetRiskRtng(derived.calNetRiskRtng())
                .grcMetrics(derived.grcMetrics())
                .commentary(derived.commentary())
                .ctrlEffRtn(derived.ctrlEffRtn())
                .status(STATUS_OPEN)
                .createdBy(userId)
                .createDtTm(now)
                .build();
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

    /** Loads and indexes all four module fact tables for the given business date. */
    private FactIndex loadFacts(LocalDate bizDt) {
        Map<DimensionKey, IncFactOrl> inc = incFactRepository.findByBizDt(bizDt).stream()
                .collect(Collectors.toMap(
                        f -> new DimensionKey(f.getRiskArea(), f.getOrlBuNmL2(), f.getOrlBuNmL3(),
                                f.getOrlBuNmL4(), f.getLocation(), f.getCategory()),
                        Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<DimensionKey, InaFactOrl> ina = inaFactRepository.findByBizDt(bizDt).stream()
                .collect(Collectors.toMap(
                        f -> new DimensionKey(f.getRiskArea(), f.getOrlBuNmL2(), f.getOrlBuNmL3(),
                                f.getOrlBuNmL4(), f.getLocation(), f.getCategory()),
                        Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<DimensionKey, KriFactOrl> kri = kriFactRepository.findByBizDt(bizDt).stream()
                .collect(Collectors.toMap(
                        f -> new DimensionKey(f.getRiskArea(), f.getOrlBuNmL2(), f.getOrlBuNmL3(),
                                f.getOrlBuNmL4(), f.getLocation(), f.getCategory()),
                        Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<DimensionKey, RcsaFactOrl> rcsa = rcsaFactRepository.findByBizDt(bizDt).stream()
                .collect(Collectors.toMap(
                        f -> new DimensionKey(f.getRiskArea(), f.getOrlBuNmL2(), f.getOrlBuNmL3(),
                                f.getOrlBuNmL4(), f.getLocation(), f.getCategory()),
                        Function.identity(), (a, b) -> a, LinkedHashMap::new));
        log.debug("Loaded facts for {}: inc={}, ina={}, kri={}, rcsa={}",
                bizDt, inc.size(), ina.size(), kri.size(), rcsa.size());
        return new FactIndex(inc, ina, kri, rcsa);
    }

    // ── Parsing helpers ────────────────────────────────────────────────────────

    /** Parses the RISK_AREA JSON string and returns its keys in insertion order. */
    private List<String> parseRiskAreaKeys(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
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
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ── Internal value types ─────────────────────────────────────────────────────

    /** One expanded CSV row before BU-hierarchy resolution. */
    private record RowSpec(String riskArea, String orlBu, String location, String category) {}

    /** Fact rows indexed by dimension key, one map per module. */
    private record FactIndex(Map<DimensionKey, IncFactOrl> inc,
                             Map<DimensionKey, InaFactOrl> ina,
                             Map<DimensionKey, KriFactOrl> kri,
                             Map<DimensionKey, RcsaFactOrl> rcsa) {

        /** The module fact rows matching the given key (each may be null). */
        MatchedFactRows match(DimensionKey key) {
            return new MatchedFactRows(inc.get(key), ina.get(key), kri.get(key), rcsa.get(key));
        }
    }
}
