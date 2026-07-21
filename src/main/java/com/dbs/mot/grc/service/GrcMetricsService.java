package com.dbs.mot.grc.service;

import com.dbs.mot.grc.common.enums.PersistableEnum;
import com.dbs.mot.grc.dto.DimensionKey;
import com.dbs.mot.grc.entity.ModuleFact;
import com.dbs.mot.grc.repository.IncFactOrlRepository;
import com.dbs.mot.grc.repository.InaFactOrlRepository;
import com.dbs.mot.grc.repository.KriFactOrlRepository;
import com.dbs.mot.grc.repository.RcsaFactOrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles the per-module GRC metrics block for a dimension, sourced from the module snapshot
 * tables ({@code rcsa_fact_orl}, {@code inc_fact_orl}, {@code ina_fact_orl}, {@code kri_fact_orl}).
 *
 * <p>The result is an ordered map keyed by module ({@code RCSA/INC/INA/KRI}); each module block
 * carries its {@code nrr} + {@code risk_rating_chge} followed by the module's metric fields. A
 * module is included only when a matching row exists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrcMetricsService {

    private final RcsaFactOrlRepository rcsaRepository;
    private final IncFactOrlRepository incRepository;
    private final InaFactOrlRepository inaRepository;
    private final KriFactOrlRepository kriRepository;

    /**
     * GRC metrics for a specific business date and dimension (used for the current/previous month).
     *
     * @return ordered module→metrics map; empty when {@code bizDt} is null or no module has a row
     */
    public Map<String, Object> forBizDate(LocalDate bizDt, DimensionKey key) {
        log.debug("Assembling GRC metrics for biz_dt={} key={}", bizDt, key);
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (bizDt == null) {
            return metrics;
        }
        addModule(metrics, rcsaRepository.findByBizDtAndDimension(bizDt, key.riskArea(),
                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()));
        addModule(metrics, incRepository.findByBizDtAndDimension(bizDt, key.riskArea(),
                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()));
        addModule(metrics, inaRepository.findByBizDtAndDimension(bizDt, key.riskArea(),
                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()));
        addModule(metrics, kriRepository.findByBizDtAndDimension(bizDt, key.riskArea(),
                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()));
        log.debug("Assembled {} module block(s) for biz_dt={}", metrics.size(), bizDt);
        return metrics;
    }

    /**
     * Live GRC metrics: each module's most recent row for the dimension (independent latest per
     * module).
     *
     * @return ordered module→metrics map; empty when no module has a row for the dimension
     */
    public Map<String, Object> live(DimensionKey key) {
        log.debug("Assembling live GRC metrics for key={}", key);
        Map<String, Object> metrics = new LinkedHashMap<>();
        addModule(metrics, rcsaRepository.findLatestByDimension(key.riskArea(),
                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()));
        addModule(metrics, incRepository.findLatestByDimension(key.riskArea(),
                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()));
        addModule(metrics, inaRepository.findLatestByDimension(key.riskArea(),
                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()));
        addModule(metrics, kriRepository.findLatestByDimension(key.riskArea(),
                key.orlBuNmL2(), key.orlBuNmL3(), key.orlBuNmL4(), key.location()));
        log.debug("Assembled {} live module block(s) for key={}", metrics.size(), key);
        return metrics;
    }

    /** Adds one module's block ({@code nrr}, {@code risk_rating_chge}, metrics) when present. */
    private void addModule(Map<String, Object> out, Optional<? extends ModuleFact> fact) {
        fact.ifPresent(f -> {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("nrr", PersistableEnum.dbValue(f.getNetRiskRtng()));
            block.put("risk_rating_chge", PersistableEnum.dbValue(f.getRiskRtngChge()));
            block.putAll(f.metrics());
            out.put(f.moduleKey(), block);
        });
    }
}
