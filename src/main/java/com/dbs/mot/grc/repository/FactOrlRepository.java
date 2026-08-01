package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.FactOrl;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
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

    /**
     * The {@code fact_orl} rows for one business date matching a given assessment's detail dimension
     * keys — a semi-join against {@code orl_lndscp_assmt_details}, so only the facts the assessment
     * needs are read. Both sides are unique on the dimension key, so at most one fact per key.
     */
    @Query("""
            SELECT f.* FROM fact_orl f
            JOIN orl_lndscp_assmt_details d
              ON f.RISK_AREA    = d.RISK_AREA
             AND f.ORL_BU_NM_L2 = d.ORL_BU_NM_L2
             AND f.ORL_BU_NM_L3 = d.ORL_BU_NM_L3
             AND f.ORL_BU_NM_L4 = d.ORL_BU_NM_L4
             AND f.LOCATION     = d.LOCATION
            WHERE d.lndscp_assmt_id = :lndscpAssmtId
              AND f.biz_dt = :bizDt
            """)
    List<FactOrl> findMatchingByAssmtDetails(@Param("lndscpAssmtId") Long lndscpAssmtId,
                                             @Param("bizDt") LocalDate bizDt);

    /**
     * The single snapshot row for a (business date, dimension key); at most one per the unique
     * index. Derived query — all predicate columns are mapped, equality-only (dimension columns
     * are {@code NOT NULL DEFAULT ''}).
     */
    Optional<FactOrl> findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
            LocalDate bizDt, String riskArea, String orlBuNmL2, String orlBuNmL3,
            String orlBuNmL4, String location);

    /**
     * The latest business date present in {@code fact_orl}, or {@code null} when the table is empty.
     */
    @Query("SELECT MAX(biz_dt) FROM fact_orl")
    LocalDate findMaxBizDt();

    /**
     * The latest {@code fact_orl.biz_dt} within an inclusive date range, or {@code null} when the
     * range holds no snapshot. Used by assessment generation to resolve a reported month's business
     * date (the month-end, else the latest snapshot within the month).
     */
    @Query("SELECT MAX(biz_dt) FROM fact_orl WHERE biz_dt BETWEEN :start AND :end")
    LocalDate findMaxBizDtBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
