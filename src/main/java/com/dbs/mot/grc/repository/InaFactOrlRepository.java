package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.InaFactOrl;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Read access to {@code ina_fact_orl}. Matched by {@code biz_dt} + the shared dimension key.
 */
@Repository
public interface InaFactOrlRepository extends CrudRepository<InaFactOrl, Long> {

    @Query("""
            SELECT * FROM ina_fact_orl
            WHERE biz_dt = :bizDt AND RISK_AREA = :riskArea
              AND ORL_BU_NM_L2 = :l2 AND ORL_BU_NM_L3 = :l3 AND ORL_BU_NM_L4 = :l4 AND LOCATION = :location
            """)
    Optional<InaFactOrl> findByBizDtAndDimension(@Param("bizDt") LocalDate bizDt,
                                                 @Param("riskArea") String riskArea,
                                                 @Param("l2") String l2, @Param("l3") String l3,
                                                 @Param("l4") String l4, @Param("location") String location);

    @Query("""
            SELECT * FROM ina_fact_orl
            WHERE biz_dt = (SELECT MAX(biz_dt) FROM ina_fact_orl
                            WHERE RISK_AREA = :riskArea AND ORL_BU_NM_L2 = :l2 AND ORL_BU_NM_L3 = :l3
                              AND ORL_BU_NM_L4 = :l4 AND LOCATION = :location)
              AND RISK_AREA = :riskArea AND ORL_BU_NM_L2 = :l2 AND ORL_BU_NM_L3 = :l3
              AND ORL_BU_NM_L4 = :l4 AND LOCATION = :location
            """)
    Optional<InaFactOrl> findLatestByDimension(@Param("riskArea") String riskArea,
                                               @Param("l2") String l2, @Param("l3") String l3,
                                               @Param("l4") String l4, @Param("location") String location);
}
