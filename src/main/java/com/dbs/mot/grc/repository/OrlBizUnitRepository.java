package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlBizUnit;
import org.springframework.data.repository.CrudRepository;
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
}
