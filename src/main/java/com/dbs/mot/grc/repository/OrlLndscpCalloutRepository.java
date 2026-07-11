package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlLndscpCallout;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JDBC repository for {@code orl_lndscp_callout}.
 *
 * <p>Soft-deleted rows ({@code DEL_FLG = TRUE}) are excluded from active queries.
 * Hard updates and soft-deletes are performed via {@code JdbcTemplate} in the service layer
 * (Spring Data JDBC's {@code save()} always issues a full-row REPLACE, which is wasteful
 * for single-field updates).
 */
@Repository
public interface OrlLndscpCalloutRepository extends CrudRepository<OrlLndscpCallout, Long> {

    /**
     * Returns all non-deleted callouts for the given assessment, ordered by id.
     * Plain derived query — no hand-written SQL.
     *
     * @param lndscpAssmtId {@code orl_lndscp_assmt.id}
     */
    List<OrlLndscpCallout> findByLndscpAssmtIdAndDelFlgFalseOrderById(Long lndscpAssmtId);
}
