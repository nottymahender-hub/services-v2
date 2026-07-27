package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.IncFactOrl;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Read access to {@code inc_fact_orl}. Matched by {@code biz_dt} + the shared dimension key,
 * expressed as a derived query.
 */
@Repository
public interface IncFactOrlRepository extends CrudRepository<IncFactOrl, Long> {

    /** The single snapshot row for a (business date, dimension key); at most one per the unique index. */
    Optional<IncFactOrl> findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
            LocalDate bizDt, String riskArea, String orlBuNmL2, String orlBuNmL3,
            String orlBuNmL4, String location);
}
