package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlLndscpDim;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrlLndscpDimRepository extends CrudRepository<OrlLndscpDim, Long> {
}
