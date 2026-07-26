package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlBizUnit;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrlBizUnitRepository extends CrudRepository<OrlBizUnit, Integer> {

    /**
     * Returns every business unit at the given hierarchy level. The assessment
     * generation service fetches all rows for the landscape's {@code BIZ_UNIT_LVL}
     * once, then resolves each BU name to its (L2, L3, L4) path in memory — avoiding
     * a per-BU query.
     *
     * @param lvlOfHier hierarchy level (2, 3 or 4)
     */
    List<OrlBizUnit> findByLvlOfHier(Integer lvlOfHier);

    /** Highest hierarchy level present, or 0 when the table is empty. */
    @Query("SELECT COALESCE(MAX(LVL_OF_HIER), 0) FROM orl_biz_unit")
    int findMaxLvlOfHier();

    /** Business-unit names at the given hierarchy level (used by landscape-dimension validation). */
    @Query("SELECT BU_NM FROM orl_biz_unit WHERE LVL_OF_HIER = :level")
    List<String> findBuNamesByLvlOfHier(@Param("level") int level);
}
