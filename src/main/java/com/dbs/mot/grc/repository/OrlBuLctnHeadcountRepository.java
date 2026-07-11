package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlBuLctnHeadcount;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrlBuLctnHeadcountRepository extends CrudRepository<OrlBuLctnHeadcount, Long> {
}
