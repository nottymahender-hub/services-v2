package com.dbs.mot.grc.service;

import com.dbs.mot.grc.common.enums.PersistableEnum;
import com.dbs.mot.grc.dto.DimensionKey;
import com.dbs.mot.grc.entity.ModuleFact;
import com.dbs.mot.grc.repository.IncFactOrlRepository;
import com.dbs.mot.grc.repository.InaFactOrlRepository;
import com.dbs.mot.grc.repository.KriFactOrlRepository;
import com.dbs.mot.grc.repository.RcsaFactOrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Assembles the per-module GRC metrics block for a dimension, sourced from the module snapshot
 * tables ({@code rcsa_fact_orl}, {@code inc_fact_orl}, {@code ina_fact_orl}, {@code kri_fact_orl}).
 *
 * <p>The result is an ordered map keyed by module ({@code RCSA/INC/INA/KRI}). <strong>Every module
 * is always present</strong> so the JSON shape never varies between dimensions: a module with a
 * matching snapshot row maps to a block carrying its {@code nrr} + {@code risk_rating_chge}
 * followed by that module's metric fields, and a module with no matching row maps to
 * {@code null}. Callers therefore only ever null-check the module value, never the key.
 */
@Slf4j
@Service
public class GrcMetricsService {

    /**
     * The modules to assemble, in JSON output order. Declaring them once keeps the "all four
     * modules, always, in this order" guarantee in a single place and lets both lookup strategies
     * ({@link #forBizDate} and {@link #live}) share one assembly loop.
     */
    private final List<ModuleSource> moduleSources;

    /**
     * Binds each module to its two repository lookups. The repositories are captured by the
     * lookup lambdas rather than held as fields, so this list is the service's only state.
     */
    public GrcMetricsService(RcsaFactOrlRepository rcsaRepository,
                            IncFactOrlRepository incRepository,
                            InaFactOrlRepository inaRepository,
                            KriFactOrlRepository kriRepository) {
        this.moduleSources = List.of(
                new ModuleSource("RCSA",
                        (bizDt, key) -> rcsaRepository.findByBizDtAndDimension(bizDt, key.riskArea(),
                                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()),
                        key -> rcsaRepository.findLatestByDimension(key.riskArea(),
                                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())),
                new ModuleSource("INC",
                        (bizDt, key) -> incRepository.findByBizDtAndDimension(bizDt, key.riskArea(),
                                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()),
                        key -> incRepository.findLatestByDimension(key.riskArea(),
                                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())),
                new ModuleSource("INA",
                        (bizDt, key) -> inaRepository.findByBizDtAndDimension(bizDt, key.riskArea(),
                                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()),
                        key -> inaRepository.findLatestByDimension(key.riskArea(),
                                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())),
                new ModuleSource("KRI",
                        (bizDt, key) -> kriRepository.findByBizDtAndDimension(bizDt, key.riskArea(),
                                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()),
                        key -> kriRepository.findLatestByDimension(key.riskArea(),
                                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())));
    }

    /**
     * GRC metrics for a specific business date and dimension (used for the current/previous month).
     *
     * @param bizDt the business date to match; when {@code null} no lookup is attempted and every
     *              module maps to {@code null}
     * @param key   the assessment dimension to match
     * @return ordered module→block map containing all four modules, each block possibly {@code null}
     */
    public Map<String, Object> forBizDate(LocalDate bizDt, DimensionKey key) {
        log.debug("Assembling GRC metrics for biz_dt={} key={}", bizDt, key);
        if (bizDt == null) {
            log.debug("No business date supplied; returning all-null GRC metrics for key={}", key);
            return emptyMetrics();
        }
        Map<String, Object> metrics = assemble(source -> source.byBizDt().apply(bizDt, key));
        logAssembled(metrics, "biz_dt=" + bizDt);
        return metrics;
    }

    /**
     * Live GRC metrics: each module's most recent row for the dimension (independent latest per
     * module).
     *
     * @param key the assessment dimension to match
     * @return ordered module→block map containing all four modules, each block possibly {@code null}
     */
    public Map<String, Object> live(DimensionKey key) {
        log.debug("Assembling live GRC metrics for key={}", key);
        Map<String, Object> metrics = assemble(source -> source.latest().apply(key));
        logAssembled(metrics, "live key=" + key);
        return metrics;
    }

    /**
     * Runs {@code lookup} for every module in declaration order and maps each to its block, or to
     * {@code null} when the module has no matching row.
     */
    private Map<String, Object> assemble(Function<ModuleSource, Optional<? extends ModuleFact>> lookup) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        for (ModuleSource source : moduleSources) {
            metrics.put(source.moduleKey(), lookup.apply(source).map(this::toBlock).orElse(null));
        }
        return metrics;
    }

    /** All four module keys mapped to {@code null} — used when no lookup can be performed. */
    private Map<String, Object> emptyMetrics() {
        return assemble(source -> Optional.empty());
    }

    /** Builds one module's block: its ratings followed by the module-specific metric fields. */
    private Map<String, Object> toBlock(ModuleFact fact) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("nrr", PersistableEnum.dbValue(fact.getNetRiskRtng()));
        block.put("risk_rating_chge", PersistableEnum.dbValue(fact.getRiskRtngChge()));
        block.putAll(fact.metrics());
        return block;
    }

    /** Logs which modules resolved to data, to make an unexpectedly empty block easy to diagnose. */
    private void logAssembled(Map<String, Object> metrics, String context) {
        if (!log.isDebugEnabled()) {
            return;
        }
        List<String> populated = metrics.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(Map.Entry::getKey)
                .toList();
        log.debug("Assembled GRC metrics for {}: {} of {} module(s) populated {}",
                context, populated.size(), metrics.size(), populated);
    }

    /**
     * One module's identity and its two lookup strategies.
     *
     * @param moduleKey the JSON key for the module, e.g. {@code "RCSA"}
     * @param byBizDt   finds the module's row for an exact business date + dimension
     * @param latest    finds the module's most recent row for a dimension
     */
    private record ModuleSource(
            String moduleKey,
            BiFunction<LocalDate, DimensionKey, Optional<? extends ModuleFact>> byBizDt,
            Function<DimensionKey, Optional<? extends ModuleFact>> latest) {
    }
}
