package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.RcsaFactOrl;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Read access to {@code rcsa_fact_orl}. Matched by {@code biz_dt} + the shared dimension key
 * ({@code NOT NULL DEFAULT ''} columns → plain equality).
 */
@Repository
public interface RcsaFactOrlRepository extends CrudRepository<RcsaFactOrl, Long> {

    @Query("""
            SELECT * FROM rcsa_fact_orl
            WHERE biz_date = :bizDt AND orl_risk_area = :riskArea
              AND orl_unit_l2 = :l2 AND orl_unit_l3 = :l3 AND orl_unit_l4 = :l4 AND orl_location = :location
            """)
    Optional<RcsaFactOrl> findByBizDtAndDimension(@Param("bizDt") LocalDate bizDt,
                                                  @Param("riskArea") String riskArea,
                                                  @Param("l2") String l2, @Param("l3") String l3,
                                                  @Param("l4") String l4, @Param("location") String location);

    @Query("""
            SELECT * FROM rcsa_fact_orl
            WHERE orl_risk_area = :riskArea
              AND orl_unit_l2 = :l2 AND orl_unit_l3 = :l3 AND orl_unit_l4 = :l4 AND orl_location = :location
            ORDER BY biz_date DESC
            LIMIT 1
            """)
    Optional<RcsaFactOrl> findLatestByDimension(@Param("riskArea") String riskArea,
                                                @Param("l2") String l2, @Param("l3") String l3,
                                                @Param("l4") String l4, @Param("location") String location);
}
