package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.FactOrl;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for {@code fact_orl}. Both queries are plain single-table lookups;
 * dimension matching (which involves nullable columns) is done in the service layer
 * rather than via derived queries, so {@code IS NULL} semantics are handled correctly.
 */
@Repository
public interface FactOrlRepository extends CrudRepository<FactOrl, Long> {

    /** All snapshot rows for a given business date (one assessment's worth of dimensions). */
    List<FactOrl> findByBizDt(LocalDate bizDt);

    /**
     * All snapshot rows (across every business date) for a given risk area — used to find
     * the latest-{@code biz_dt} row for a dimension when building the live NRR snapshot.
     */
    List<FactOrl> findByRiskArea(String riskArea);
}
