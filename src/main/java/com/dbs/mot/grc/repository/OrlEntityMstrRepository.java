package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlEntityMstr;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrlEntityMstrRepository extends CrudRepository<OrlEntityMstr, Integer> {

    /**
     * Distinct {@code orl_location} values, used by landscape-dimension validation to check that
     * every uploaded location is a known entity-master location.
     */
    @Query("SELECT DISTINCT orl_location FROM orl_entity_mstr")
    List<String> findDistinctOrlLocations();
}
