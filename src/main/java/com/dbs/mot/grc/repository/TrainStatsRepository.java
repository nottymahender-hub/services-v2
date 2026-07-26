package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.dto.TrainStatsVersionGroup;
import com.dbs.mot.grc.entity.TrainStats;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainStatsRepository extends CrudRepository<TrainStats, Integer> {

    /** Current maximum {@code config_version} for every natural-key group {@code (lvl, module)}. */
    @Query("""
            SELECT lvl, module, MAX(config_version) AS max_version
            FROM train_stats
            GROUP BY lvl, module
            """)
    List<TrainStatsVersionGroup> findGroupedMaxVersions();
}
