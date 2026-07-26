package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.dto.NetRiskBandVersionGroup;
import com.dbs.mot.grc.entity.NetRiskBand;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NetRiskBandRepository extends CrudRepository<NetRiskBand, Integer> {

    /** Current maximum {@code config_version} for every natural-key group {@code (net_risk_rtng, module)}. */
    @Query("""
            SELECT net_risk_rtng, module, MAX(config_version) AS max_version
            FROM net_risk_band
            GROUP BY net_risk_rtng, module
            """)
    List<NetRiskBandVersionGroup> findGroupedMaxVersions();
}
