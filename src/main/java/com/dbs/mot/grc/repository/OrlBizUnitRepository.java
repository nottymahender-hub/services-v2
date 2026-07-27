package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlBizUnit;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrlBizUnitRepository extends CrudRepository<OrlBizUnit, Integer> {

    /**
     * Highest hierarchy level present, or 0 when the table is empty. Kept as an explicit aggregate
     * query (Spring Data JDBC derived queries cannot express {@code COALESCE(MAX(...), 0)}).
     */
    @Query("SELECT COALESCE(MAX(LVL_OF_HIER), 0) FROM orl_biz_unit")
    int findMaxLvlOfHier();
}
