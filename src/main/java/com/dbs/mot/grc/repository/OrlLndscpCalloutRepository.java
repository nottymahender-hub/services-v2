package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlLndscpCallout;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JDBC repository for {@code orl_lndscp_callout}.
 *
 * <p>Soft-deleted rows ({@code DEL_FLG = TRUE}) are excluded from active queries. Updates and
 * soft-deletes go through {@code save()} in the service — the entity has no child collection, so
 * {@code save()} is a plain single-row update.
 */
@Repository
public interface OrlLndscpCalloutRepository extends CrudRepository<OrlLndscpCallout, Long> {

    /**
     * Returns all non-deleted callouts for the given assessment, ordered by id.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     */
    List<OrlLndscpCallout> findByLndscpAssmtIdAndDelFlgFalseOrderById(Long lndscpAssmtId);
}
