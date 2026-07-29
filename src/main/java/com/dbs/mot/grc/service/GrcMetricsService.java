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
 * Assembles the per-module GRC metrics blocks for a dimension, sourced from the module snapshot
 * tables ({@code rcsa_fact_orl}, {@code inc_fact_orl}, {@code ina_fact_orl}, {@code kri_fact_orl}).
 *
 * <p>The result is an ordered map keyed by module ({@code RCSA/INC/INA/KRI}). <strong>Every module
 * is always present and every block is fully populated</strong> so the response shape never varies:
 * each {@link GrcModuleBlock} carries the module {@code nrr} (its stored DB value, e.g.
 * {@code "Med Low"}), a module-level {@code riskRatingChge}, and the complete ordered list of that
 * module's metrics. When a module has no snapshot row for the business date the block still lists
 * every metric with a {@code null} value, and {@code nrr}/all change labels are {@code "N.A"}.
 *
 * <p><strong>riskRatingChge:</strong> for the current/previous snapshots both the module-level and
 * per-metric changes are read from the assessment detail's stored {@code MODULE_RISK_RTNG_CHGE} JSON
 * (see {@link ModuleRiskRatingChanges#parse}); for the <em>live</em> snapshot they are computed on
 * the fly against the current snapshot (module-level via {@link RiskRatingChanges}, per-metric via
 * {@link ModuleRiskRatingChanges#metricChanges}).
 */
@Slf4j
@Service
public class GrcMetricsService {

    private static final String NA = ModuleRiskRatingChanges.NOT_APPLICABLE;

    /**
     * The modules to assemble, in JSON output order. Declaring them once keeps the "all four
     * modules, always, in this order" guarantee in a single place, shared by every lookup.
     */
    private final List<ModuleSource> moduleSources;

    private final ModuleRiskRatingChanges moduleRiskRatingChanges;

    /**
     * Binds each module to its lookup and its canonical metric template. The repositories are
     * captured by the lookup lambdas rather than held as fields; the template (metric names in
     * order, all values {@code null}) comes from an empty entity instance so the metric name set
     * lives only in the entity.
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
     * The raw module facts for a business date + dimension, keyed by module ({@code RCSA/INC/INA/KRI})
     * in output order. Every module key is present; a module with no matching snapshot row maps to
     * {@code null}. Used by generation (for the {@code MODULE_RISK_RTNG_CHGE} JSON) and by the
     * drill-down to fetch the current facts once and reuse them for both the current and live blocks.
     *
     * @param bizDt the business date to match; when {@code null} every module maps to {@code null}
     * @param key   the dimension to match
     * @return ordered module → fact map (values may be {@code null})
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
     * Current/previous GRC blocks: fetches the module facts for the business date and pairs them with
     * the changes parsed from the assessment detail's stored {@code MODULE_RISK_RTNG_CHGE} JSON.
     *
     * @param bizDt         the business date to match; {@code null} → every module has no fact
     * @param key           the assessment dimension to match
     * @param moduleChanges module key → parsed {@link ModuleChange} (may be empty; a missing module
     *                      resolves to {@link ModuleChange#NONE})
     * @return ordered module → fully-populated block (all four modules always present)
     */
    public Map<String, GrcModuleBlock> forBizDate(LocalDate bizDt, DimensionKey key,
                                                  Map<String, ModuleChange> moduleChanges) {
        return storedBlocks(moduleFacts(bizDt, key), moduleChanges);
    }

    /**
     * Current/previous GRC blocks from already-fetched facts (so the drill-down can fetch the current
     * facts once and reuse them for the live block). Each module's changes come from the stored JSON.
     *
     * @param facts         module key → snapshot fact (values may be {@code null})
     * @param moduleChanges module key → parsed {@link ModuleChange} from the stored JSON
     * @return ordered module → fully-populated block
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
     * Live GRC blocks (latest snapshot). Each module's changes are computed on the fly against the
     * current snapshot: the module-level change via {@link RiskRatingChanges} (live rating vs. the
     * current assessment's rating) and each metric's neutral change via
     * {@link ModuleRiskRatingChanges#metricChanges}.
     *
     * @param liveFacts    module key → live snapshot fact (values may be {@code null})
     * @param currentFacts module key → current assessment's snapshot fact (the comparison baseline)
     * @return ordered module → fully-populated block
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
     * Builds one module block. The metric list always follows the module's canonical template
     * (every metric, in order); a metric's value is taken from the fact when present, else
     * {@code null}. {@code nrr} is the fact's rating (DB value) or {@code "N.A"} when there is no row.
     *
     * @param factOrNull      the module snapshot row, or {@code null} when absent
     * @param moduleChange    the already-resolved module-level change label
     * @param metricChangeFor per-metric change lookup (already {@code "N.A"}-defaulted)
     * @param metricTemplate  the module's canonical metric names → default values
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

    /**
     * One module's identity, its lookup by exact business date + dimension, and its canonical metric
     * template (metric names in order with {@code null} values, from an empty entity instance).
     *
     * @param moduleKey      the JSON key for the module, e.g. {@code "RCSA"}
     * @param byBizDt        finds the module's row for an exact business date + dimension
     * @param metricTemplate the module's ordered metric names → default (null) values
     */
    private record ModuleSource(
            String moduleKey,
            BiFunction<LocalDate, DimensionKey, Optional<? extends ModuleFact>> byBizDt,
            Map<String, Object> metricTemplate) {
    }
}
