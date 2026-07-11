package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlRiskTypeRiskAreaMap;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrlRiskTypeRiskAreaMapRepository extends CrudRepository<OrlRiskTypeRiskAreaMap, Integer> {
}
