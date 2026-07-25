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

    /** All snapshot rows for a business date — batch-loaded once for the list endpoint. */
    List<FactOrl> findByBizDt(LocalDate bizDt);

    /** The single snapshot row for a (business date, dimension key); at most one per the unique index. */
    @Query("""
            SELECT * FROM fact_orl
            WHERE biz_dt = :bizDt AND RISK_AREA = :riskArea
              AND ORL_BU_NM_L2 = :l2 AND ORL_BU_NM_L3 = :l3 AND ORL_BU_NM_L4 = :l4 AND LOCATION = :location
            """)
    Optional<FactOrl> findByBizDtAndDimension(@Param("bizDt") LocalDate bizDt,
                                              @Param("riskArea") String riskArea,
                                              @Param("l2") String l2, @Param("l3") String l3,
                                              @Param("l4") String l4, @Param("location") String location);

    /**
     * The live snapshot row for a dimension key: the row whose {@code biz_dt} equals the MAX
     * {@code biz_dt} for that dimension. Uses an equality-on-max subquery (indexed lookup) rather
     * than {@code ORDER BY biz_dt DESC LIMIT 1} to avoid sorting the dimension's rows.
     */
    @Query("""
            SELECT * FROM fact_orl
            WHERE biz_dt = (SELECT MAX(biz_dt) FROM fact_orl
                            WHERE RISK_AREA = :riskArea AND ORL_BU_NM_L2 = :l2 AND ORL_BU_NM_L3 = :l3
                              AND ORL_BU_NM_L4 = :l4 AND LOCATION = :location)
              AND RISK_AREA = :riskArea AND ORL_BU_NM_L2 = :l2 AND ORL_BU_NM_L3 = :l3
              AND ORL_BU_NM_L4 = :l4 AND LOCATION = :location
            """)
    Optional<FactOrl> findLatestByDimension(@Param("riskArea") String riskArea,
                                            @Param("l2") String l2, @Param("l3") String l3,
                                            @Param("l4") String l4, @Param("location") String location);

    /** The latest business date present in {@code fact_orl}, or {@code null} when the table is empty. */
    @Query("SELECT MAX(biz_dt) FROM fact_orl")
    LocalDate findMaxBizDt();

    /**
     * The latest {@code biz_dt} within {@code [start, end]} (used to resolve an assessment's
     * business date to the actual month-end snapshot date present in {@code fact_orl}).
     * {@code null} when the range has no rows.
     */
    @Query("SELECT MAX(biz_dt) FROM fact_orl WHERE biz_dt BETWEEN :start AND :end")
    LocalDate findMaxBizDtBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
