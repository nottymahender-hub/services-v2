package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.DimensionKey;
import com.dbs.mot.grc.dto.GrcMetric;
import com.dbs.mot.grc.dto.GrcModuleBlock;
import com.dbs.mot.grc.entity.IncFactOrl;
import com.dbs.mot.grc.entity.InaFactOrl;
import com.dbs.mot.grc.entity.KriFactOrl;
import com.dbs.mot.grc.entity.ModuleFact;
import com.dbs.mot.grc.entity.RcsaFactOrl;
import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.enums.PersistableEnum;
import com.dbs.mot.grc.repository.IncFactOrlRepository;
import com.dbs.mot.grc.repository.InaFactOrlRepository;
import com.dbs.mot.grc.repository.KriFactOrlRepository;
import com.dbs.mot.grc.repository.RcsaFactOrlRepository;
import com.dbs.mot.grc.util.ModuleRiskRatingChanges;
import com.dbs.mot.grc.util.ModuleRiskRatingChanges.ModuleChange;
import com.dbs.mot.grc.util.RiskRatingChanges;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Assembles the per-module GRC metrics blocks (RCSA/INC/INA/KRI) for a dimension from the
 * {@code *_fact_orl} snapshot tables.
 *
 * <p>Every module is always present and every {@link GrcModuleBlock} fully populated, so the response
 * shape never varies: a module with no snapshot row still lists every metric with a {@code null}
 * value and {@code "N.A"} for {@code nrr} and all change labels. {@code nrr} is the stored DB value.
 * Current/previous {@code riskRatingChge} (module- and metric-level) comes from the detail's stored
 * {@code MODULE_RISK_RTNG_CHGE} JSON; the live block computes it on the fly against the current snapshot.
 */
@Slf4j
@Service
public class GrcMetricsService {

    private static final String NA = ModuleRiskRatingChanges.NOT_APPLICABLE;

    /** The modules to assemble, in fixed JSON output order (RCSA → INC → INA → KRI). */
    private final List<ModuleSource> moduleSources;

    private final ModuleRiskRatingChanges moduleRiskRatingChanges;

    /**
     * Binds each module to its repository lookup and its canonical metric template (metric names in
     * order, all values {@code null}, from an empty entity instance so the name set lives in the entity).
     */
    public GrcMetricsService(RcsaFactOrlRepository rcsaRepository,
                            IncFactOrlRepository incRepository,
                            InaFactOrlRepository inaRepository,
                            KriFactOrlRepository kriRepository,
                            ModuleRiskRatingChanges moduleRiskRatingChanges) {
        this.moduleRiskRatingChanges = moduleRiskRatingChanges;
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
     * {@code null} when no row / {@code bizDt} is {@code null}). Used by generation and the drill-down.
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

    /** Current/previous GRC blocks: fetches the facts for the business date, then {@link #storedBlocks}. */
    public Map<String, GrcModuleBlock> forBizDate(LocalDate bizDt, DimensionKey key,
                                                  Map<String, ModuleChange> moduleChanges) {
        return storedBlocks(moduleFacts(bizDt, key), moduleChanges);
    }

    /**
     * Current/previous GRC blocks from already-fetched facts, with module- and metric-level changes
     * taken from the parsed {@code MODULE_RISK_RTNG_CHGE} JSON ({@link ModuleChange#NONE} when absent).
     */
    public Map<String, GrcModuleBlock> storedBlocks(Map<String, ModuleFact> facts,
                                                    Map<String, ModuleChange> moduleChanges) {
        Map<String, ModuleChange> changes = moduleChanges != null ? moduleChanges : Map.of();
        Map<String, GrcModuleBlock> blocks = new LinkedHashMap<>();
        for (ModuleSource source : moduleSources) {
            ModuleFact fact = facts.get(source.moduleKey());
            ModuleChange change = changes.getOrDefault(source.moduleKey(), ModuleChange.NONE);
            // Module-level and per-metric changes both come from the stored MODULE_RISK_RTNG_CHGE JSON.
            blocks.put(source.moduleKey(),
                    block(fact, change.riskRatingChangeOrNa(), change::metricChangeOrNa, source.metricTemplate()));
        }
        logAssembled(blocks, "stored");
        return blocks;
    }

    /**
     * Live GRC blocks (latest snapshot), with changes computed on the fly against {@code currentFacts}:
     * module-level via {@link RiskRatingChanges}, per-metric via {@link ModuleRiskRatingChanges#metricChanges}.
     */
    public Map<String, GrcModuleBlock> liveBlocks(Map<String, ModuleFact> liveFacts,
                                                  Map<String, ModuleFact> currentFacts) {
        Map<String, ModuleFact> baseline = currentFacts != null ? currentFacts : Map.of();
        Map<String, GrcModuleBlock> blocks = new LinkedHashMap<>();
        for (ModuleSource source : moduleSources) {
            ModuleFact live = liveFacts.get(source.moduleKey());
            if (live == null) {
                // No live row → an all-default block (nrr and every change "N.A").
                blocks.put(source.moduleKey(), block(null, NA, name -> NA, source.metricTemplate()));
                continue;
            }
            ModuleFact current = baseline.get(source.moduleKey());
            NetRiskRating currentNrr = current != null ? current.getNetRiskRtng() : null;
            String moduleChange = PersistableEnum.dbValue(RiskRatingChanges.derive(currentNrr, live.getNetRiskRtng()));
            Map<String, String> metricChanges = moduleRiskRatingChanges.metricChanges(live, current);
            blocks.put(source.moduleKey(),
                    block(live, moduleChange, name -> metricChanges.getOrDefault(name, NA), source.metricTemplate()));
        }
        logAssembled(blocks, "live");
        return blocks;
    }

    /**
     * Builds one module block over the canonical metric template (every metric, in order): each value
     * from the fact when present else {@code null}; {@code nrr} the fact's DB value or {@code "N.A"}.
     */
    private GrcModuleBlock block(ModuleFact factOrNull, String moduleChange,
                                 UnaryOperator<String> metricChangeFor, Map<String, Object> metricTemplate) {
        String nrr = factOrNull != null ? PersistableEnum.dbValue(factOrNull.getNetRiskRtng()) : NA;
        Map<String, Object> values = factOrNull != null ? factOrNull.metrics() : Map.of();
        List<GrcMetric> metrics = new ArrayList<>(metricTemplate.size());
        for (String name : metricTemplate.keySet()) {
            metrics.add(new GrcMetric(name, values.get(name), metricChangeFor.apply(name)));
        }
        return new GrcModuleBlock(nrr, moduleChange, metrics);
    }

    /** Logs which modules resolved to a fact row, to make an unexpectedly empty block easy to diagnose. */
    private void logAssembled(Map<String, GrcModuleBlock> blocks, String context) {
        if (!log.isDebugEnabled()) {
            return;
        }
        List<String> populated = blocks.entrySet().stream()
                .filter(e -> !NA.equals(e.getValue().nrr()))
                .map(Map.Entry::getKey)
                .toList();
        log.debug("Assembled {} GRC blocks: {} of {} module(s) had a fact row {}",
                context, populated.size(), blocks.size(), populated);
    }

    /** One module's JSON key, its lookup by (business date, dimension), and its canonical metric template. */
    private record ModuleSource(
            String moduleKey,
            BiFunction<LocalDate, DimensionKey, Optional<? extends ModuleFact>> byBizDt,
            Map<String, Object> metricTemplate) {
    }
}
