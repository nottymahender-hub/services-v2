package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.FactOrl;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@code fact_orl}. Dimension columns are {@code NOT NULL DEFAULT ''}, so the
 * dimension predicates below match on plain equality (no {@code IS NULL} handling needed).
 */
@Repository
public interface FactOrlRepository extends CrudRepository<FactOrl, Long> {

    /** All snapshot rows for a business date — batch-loaded once for the list endpoint. */
    List<FactOrl> findByBizDt(LocalDate bizDt);

    /**
     * The single snapshot row for a (business date, dimension key); at most one per the unique
     * index. Derived query — all predicate columns are mapped, equality-only (dimension columns
     * are {@code NOT NULL DEFAULT ''}).
     */
    Optional<FactOrl> findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
            LocalDate bizDt, String riskArea, String orlBuNmL2, String orlBuNmL3,
            String orlBuNmL4, String location);

    /** The latest business date present in {@code fact_orl}, or {@code null} when the table is empty. */
    @Query("SELECT MAX(biz_dt) FROM fact_orl")
    LocalDate findMaxBizDt();
}
