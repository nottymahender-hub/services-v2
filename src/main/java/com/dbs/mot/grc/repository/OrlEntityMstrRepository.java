package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlEntityMstr;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrlEntityMstrRepository extends CrudRepository<OrlEntityMstr, Integer> {
}
