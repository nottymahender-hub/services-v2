package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlLndscpDim;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface OrlLndscpDimRepository extends CrudRepository<OrlLndscpDim, Long> {

    /**
     * Highest {@code VERSION} across the given {@code CONFIG_ID}s, or 0 when none exist yet.
     * The collection is bound as a single named parameter and expanded by Spring Data JDBC into
     * the {@code IN (...)} list, so no SQL is built by string concatenation.
     *
     * @param configIds the CONFIG_IDs present in the current upload
     */
    @Query("SELECT COALESCE(MAX(VERSION), 0) FROM orl_lndscp_dim WHERE CONFIG_ID IN (:configIds)")
    int findMaxVersionByConfigIds(@Param("configIds") Collection<String> configIds);
}
