package com.dbs.mot.grc.service;

import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.enums.PersistableEnum;
import com.dbs.mot.grc.enums.RiskRatingChange;
import com.dbs.mot.grc.dto.DimensionKey;
import com.dbs.mot.grc.entity.ModuleFact;
import com.dbs.mot.grc.repository.IncFactOrlRepository;
import com.dbs.mot.grc.repository.InaFactOrlRepository;
import com.dbs.mot.grc.repository.KriFactOrlRepository;
import com.dbs.mot.grc.repository.RcsaFactOrlRepository;
import com.dbs.mot.grc.util.RiskRatingChanges;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Assembles the per-module GRC metrics block for a dimension, sourced from the module snapshot
 * tables ({@code rcsa_fact_orl}, {@code inc_fact_orl}, {@code ina_fact_orl}, {@code kri_fact_orl}).
 *
 * <p>The result ({@link ModuleGrcMetrics}) carries an ordered {@code blocks} map keyed by module
 * ({@code RCSA/INC/INA/KRI}) plus each module's net risk rating (for downstream comparisons).
 * <strong>Every module is always present</strong> in {@code blocks} so the JSON shape never varies:
 * a module with a matching snapshot row maps to a block carrying its {@code nrr} (in display form,
 * e.g. {@code "Medium-Low Risk"}) + {@code risk_rating_chge} followed by that module's metric
 * fields, and a module with no matching row maps to {@code null}.
 *
 * <p><strong>risk_rating_chge:</strong> for the current/previous snapshots it is the module fact's
 * stored value; for the <em>live</em> snapshot it is <em>derived</em> by comparing the live module
 * rating against the current assessment's module rating (see {@link #forLive} and the single-source
 * rule in {@link RiskRatingChanges}).
 */
@Slf4j
@Service
public class GrcMetricsService {

    /**
     * The modules to assemble, in JSON output order. Declaring them once keeps the "all four
     * modules, always, in this order" guarantee in a single place, shared by every
     * {@link #forBizDate} lookup.
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
                        (bizDt, key) -> rcsaRepository.findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
                                bizDt, key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())),
                new ModuleSource("INC",
                        (bizDt, key) -> incRepository.findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
                                bizDt, key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())),
                new ModuleSource("INA",
                        (bizDt, key) -> inaRepository.findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
                                bizDt, key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())),
                new ModuleSource("KRI",
                        (bizDt, key) -> kriRepository.findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
                                bizDt, key.riskArea(), key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location())));
    }

    /**
     * GRC metrics for a specific business date and dimension. Used for the current, previous and
     * live snapshots alike — the caller passes the relevant business date (the assessment's own
     * {@code biz_dt} for current/previous, or the latest {@code fact_orl.biz_dt} for live).
     *
     * @param bizDt the business date to match; when {@code null} no lookup is attempted and every
     *              module maps to {@code null}
     * @param key   the assessment dimension to match
     * @return ordered module→block map containing all four modules, each block possibly {@code null}
     */
    public ModuleGrcMetrics forBizDate(LocalDate bizDt, DimensionKey key) {
        log.debug("Assembling GRC metrics for biz_dt={} key={}", bizDt, key);
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, NetRiskRating> netRiskRatings = new LinkedHashMap<>();
        for (ModuleSource source : moduleSources) {
            ModuleFact fact = (bizDt == null ? Optional.<ModuleFact>empty() : source.byBizDt().apply(bizDt, key))
                    .orElse(null);
            // Current/previous snapshots use the module fact's own stored risk-rating change.
            blocks.put(source.moduleKey(),
                    fact == null ? null : block(fact, PersistableEnum.dbValue(fact.getRiskRtngChge())));
            netRiskRatings.put(source.moduleKey(), fact == null ? null : fact.getNetRiskRtng());
        }
        logAssembled(blocks, "biz_dt=" + bizDt);
        return new ModuleGrcMetrics(blocks, netRiskRatings);
    }

    /**
     * GRC metrics for the live snapshot (latest business date). Identical to {@link #forBizDate}
     * except each module's {@code risk_rating_chge} is <em>derived</em> — the live module rating
     * compared against {@code comparisonNrrs} (the current assessment's per-module ratings) via
     * {@link RiskRatingChanges}.
     *
     * @param maxBizDt       the latest business date across the module facts
     * @param key            the dimension to match
     * @param comparisonNrrs the current assessment's per-module net risk ratings (the baseline)
     */
    public ModuleGrcMetrics forLive(LocalDate maxBizDt, DimensionKey key,
                                    Map<String, NetRiskRating> comparisonNrrs) {
        log.debug("Assembling live GRC metrics for biz_dt={} key={}", maxBizDt, key);
        Map<String, NetRiskRating> baseline = comparisonNrrs != null ? comparisonNrrs : Map.of();
        Map<String, Object> blocks = new LinkedHashMap<>();
        Map<String, NetRiskRating> netRiskRatings = new LinkedHashMap<>();
        for (ModuleSource source : moduleSources) {
            ModuleFact fact = (maxBizDt == null ? Optional.<ModuleFact>empty() : source.byBizDt().apply(maxBizDt, key))
                    .orElse(null);
            if (fact == null) {
                blocks.put(source.moduleKey(), null);
                netRiskRatings.put(source.moduleKey(), null);
                continue;
            }
            RiskRatingChange change = RiskRatingChanges.derive(baseline.get(source.moduleKey()), fact.getNetRiskRtng());
            blocks.put(source.moduleKey(), block(fact, PersistableEnum.dbValue(change)));
            netRiskRatings.put(source.moduleKey(), fact.getNetRiskRtng());
        }
        logAssembled(blocks, "live biz_dt=" + maxBizDt);
        return new ModuleGrcMetrics(blocks, netRiskRatings);
    }

    /**
     * Builds one module's block: its net risk rating (display form) and risk-rating change,
     * followed by the module-specific metric fields.
     *
     * @param fact           the module snapshot row
     * @param riskRatingChge the risk-rating-change db value already resolved by the caller
     *                       (stored for current/previous, derived for live)
     */
    private Map<String, Object> block(ModuleFact fact, String riskRatingChge) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("nrr", NetRiskRating.display(fact.getNetRiskRtng()));
        block.put("risk_rating_chge", riskRatingChge);
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
     * One module's identity and its lookup by exact business date + dimension.
     *
     * @param moduleKey the JSON key for the module, e.g. {@code "RCSA"}
     * @param byBizDt   finds the module's row for an exact business date + dimension
     */
    private record ModuleSource(
            String moduleKey,
            BiFunction<LocalDate, DimensionKey, Optional<? extends ModuleFact>> byBizDt) {
    }

    /**
     * Assembled GRC metrics for one snapshot: the module→block map for the API response, plus each
     * module's net risk rating so a caller can derive a downstream comparison (e.g. the live block's
     * risk-rating change vs. the current assessment). Both maps always contain all four module keys.
     *
     * @param blocks         module → JSON block (or {@code null} when the module has no row)
     * @param netRiskRatings module → net risk rating (or {@code null} when the module has no row)
     */
    public record ModuleGrcMetrics(Map<String, Object> blocks, Map<String, NetRiskRating> netRiskRatings) {
    }
}
