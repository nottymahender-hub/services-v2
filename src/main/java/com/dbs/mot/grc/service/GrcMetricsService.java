package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.DimensionKey;
import com.dbs.mot.grc.dto.GrcMetric;
import com.dbs.mot.grc.dto.GrcModuleBlock;
import com.dbs.mot.grc.entity.IncFactOrl;
import com.dbs.mot.grc.entity.InaFactOrl;
import com.dbs.mot.grc.entity.KriFactOrl;
import com.dbs.mot.grc.entity.ModuleFact;
import com.dbs.mot.grc.entity.RcsaFactOrl;
import com.dbs.mot.grc.enums.PersistableEnum;
import com.dbs.mot.grc.repository.IncFactOrlRepository;
import com.dbs.mot.grc.repository.InaFactOrlRepository;
import com.dbs.mot.grc.repository.KriFactOrlRepository;
import com.dbs.mot.grc.repository.RcsaFactOrlRepository;
import com.dbs.mot.grc.util.RiskRatingChanges;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Assembles the per-module GRC metrics blocks (RCSA/INC/INA/KRI) for a dimension, always by
 * comparing a <b>target</b> snapshot against a <b>baseline</b> snapshot:
 *
 * <pre>
 *   current block  = target: this assessment's own module facts   baseline: previous assessment's module facts
 *   previous block = target: previous assessment's module facts   baseline: the assessment before that
 *   live block     = target: today's latest module facts          baseline: this assessment's own module facts
 * </pre>
 *
 * <p>The comparison itself ({@link #buildBlocks}) is the same algorithm every time, so there is only
 * one place that builds a block. Every module is always present and every {@link GrcModuleBlock} is
 * fully populated: a module with no target fact still lists every metric with a {@code null} value
 * and {@code "N.A"} for {@code nrr} and all change labels.
 */
@Slf4j
@Service
public class GrcMetricsService {

    private static final String NOT_APPLICABLE = "N.A";
    private static final String INCREASED = "Increased";
    private static final String DECREASED = "Decreased";
    /** Metric-level "equal" label. Distinct from the module-level {@code RiskRatingChange.STABLE} ("Stable"). */
    private static final String NO_CHANGE = "No change";

    /** The modules to assemble, in fixed JSON output order (RCSA → INC → INA → KRI). */
    private final List<ModuleSource> moduleSources;

    /**
     * Binds each module to its repository lookup and its canonical metric template (metric names in
     * order, all values {@code null}, from an empty entity instance so the name set lives in the entity).
     */
    public GrcMetricsService(RcsaFactOrlRepository rcsaRepository,
                            IncFactOrlRepository incRepository,
                            InaFactOrlRepository inaRepository,
                            KriFactOrlRepository kriRepository) {
        this.moduleSources = List.of(
                new ModuleSource("RCSA",
                        (bizDt, key) -> rcsaRepository.findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
                                bizDt, key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()),
                        new RcsaFactOrl().metrics()),
                new ModuleSource("INC",
                        (bizDt, key) -> incRepository.findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
                                bizDt, key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()),
                        new IncFactOrl().metrics()),
                new ModuleSource("INA",
                        (bizDt, key) -> inaRepository.findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
                                bizDt, key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()),
                        new InaFactOrl().metrics()),
                new ModuleSource("KRI",
                        (bizDt, key) -> kriRepository.findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
                                bizDt, key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()),
                        new KriFactOrl().metrics()));
    }

    /**
     * The raw module facts for a business date + dimension, keyed by module in output order (values
     * {@code null} when no row / {@code bizDt} is {@code null}).
     */
    public Map<String, ModuleFact> moduleFacts(LocalDate bizDt, DimensionKey key) {
        Map<String, ModuleFact> facts = new LinkedHashMap<>();
        for (ModuleSource source : moduleSources) {
            ModuleFact fact = (bizDt == null ? Optional.<ModuleFact>empty() : source.byBizDt().apply(bizDt, key))
                    .orElse(null);
            facts.put(source.moduleKey(), fact);
        }
        return facts;
    }

    /**
     * Builds the GRC blocks for all four modules by comparing {@code targetFacts} against
     * {@code baselineFacts}. For each module:
     * <ol>
     *   <li>{@code nrr} = the target fact's net risk rating (DB value), or {@code "N.A"} when there
     *       is no target fact;</li>
     *   <li>the module-level change = {@link RiskRatingChanges#derive} of baseline NRR vs. target NRR;</li>
     *   <li>each metric's value comes from the target fact (or {@code null}); its change compares the
     *       target value against the baseline value (see {@link #compareMetric}).</li>
     * </ol>
     *
     * @param targetFacts   module key → the snapshot being reported on (may be empty/missing per module)
     * @param baselineFacts module key → the snapshot to compare against (may be empty/missing per module)
     * @return ordered module → fully-populated block (all four modules always present)
     */
    public Map<String, GrcModuleBlock> buildBlocks(Map<String, ModuleFact> targetFacts,
                                                   Map<String, ModuleFact> baselineFacts) {
        Map<String, GrcModuleBlock> blocks = new LinkedHashMap<>();
        for (ModuleSource source : moduleSources) {
            ModuleFact target = targetFacts.get(source.moduleKey());
            ModuleFact baseline = baselineFacts.get(source.moduleKey());
            blocks.put(source.moduleKey(), buildOneBlock(target, baseline, source.metricTemplate()));
        }
        logAssembled(blocks);
        return blocks;
    }

    /** Builds one module's block by comparing its target fact against its baseline fact. */
    private GrcModuleBlock buildOneBlock(ModuleFact target, ModuleFact baseline, Map<String, Object> metricTemplate) {
        String nrr = target != null ? PersistableEnum.dbValue(target.getNetRiskRtng()) : NOT_APPLICABLE;
        String moduleChange = target != null
                ? PersistableEnum.dbValue(RiskRatingChanges.derive(
                        baseline != null ? baseline.getNetRiskRtng() : null, target.getNetRiskRtng()))
                : NOT_APPLICABLE;

        Map<String, Object> targetValues = target != null ? target.metrics() : Map.of();
        Map<String, Object> baselineValues = baseline != null ? baseline.metrics() : Map.of();

        List<GrcMetric> metrics = new ArrayList<>(metricTemplate.size());
        for (String metricName : metricTemplate.keySet()) {
            Object value = targetValues.get(metricName);
            String change = compareMetric(baselineValues.get(metricName), value);
            metrics.add(new GrcMetric(metricName, value, change));
        }
        return new GrcModuleBlock(nrr, moduleChange, metrics);
    }

    /**
     * Neutral (no risk-direction judgement) change label for one metric: {@code "Increased"} when
     * the target value is numerically greater than the baseline, {@code "Decreased"} when smaller,
     * {@code "No change"} when equal, and {@code "N.A"} when either value is missing or non-numeric.
     */
    private String compareMetric(Object baselineValue, Object targetValue) {
        BigDecimal baseline = toBigDecimal(baselineValue);
        BigDecimal target = toBigDecimal(targetValue);
        if (baseline == null || target == null) {
            return NOT_APPLICABLE;
        }
        int comparison = target.compareTo(baseline);
        if (comparison > 0) {
            return INCREASED;
        }
        return comparison < 0 ? DECREASED : NO_CHANGE;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return null;
    }

    /** Logs which modules resolved to a target fact row, to make an unexpectedly empty block easy to diagnose. */
    private void logAssembled(Map<String, GrcModuleBlock> blocks) {
        if (!log.isDebugEnabled()) {
            return;
        }
        List<String> populated = blocks.entrySet().stream()
                .filter(e -> !NOT_APPLICABLE.equals(e.getValue().nrr()))
                .map(Map.Entry::getKey)
                .toList();
        log.debug("Assembled GRC blocks: {} of {} module(s) had a target fact row {}",
                populated.size(), blocks.size(), populated);
    }

    /** One module's JSON key, its lookup by (business date, dimension), and its canonical metric template. */
    private record ModuleSource(
            String moduleKey,
            BiFunction<LocalDate, DimensionKey, Optional<? extends ModuleFact>> byBizDt,
            Map<String, Object> metricTemplate) {
    }
}
