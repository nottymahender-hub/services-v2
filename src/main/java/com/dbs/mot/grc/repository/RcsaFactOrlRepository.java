package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.RcsaFactOrl;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Read access to {@code rcsa_fact_orl}. Matched by {@code biz_dt} + the shared dimension key
 * ({@code NOT NULL DEFAULT ''} columns → plain equality), expressed as a derived query.
 */
@Repository
public interface RcsaFactOrlRepository extends CrudRepository<RcsaFactOrl, Long> {

    /** The single snapshot row for a (business date, dimension key); at most one per the unique index. */
    Optional<RcsaFactOrl> findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
            LocalDate bizDt, String riskArea, String orlBuNmL2, String orlBuNmL3,
            String orlBuNmL4, String location);
}
