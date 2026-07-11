package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlLndscpDim;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrlLndscpDimRepository extends CrudRepository<OrlLndscpDim, Long> {

    /**
     * All config rows for the given landscape name (every version / effective window).
     * Filtering to the active, currently-effective row is business logic and lives in
     * the service layer.
     */
    List<OrlLndscpDim> findByLndscpNm(String lndscpNm);
}
