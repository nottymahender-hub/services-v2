package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.FeatureScoreBand;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeatureScoreBandRepository extends CrudRepository<FeatureScoreBand, Long> {
}
