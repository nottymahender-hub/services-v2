package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.TrainStats;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainStatsRepository extends CrudRepository<TrainStats, Integer> {
}
