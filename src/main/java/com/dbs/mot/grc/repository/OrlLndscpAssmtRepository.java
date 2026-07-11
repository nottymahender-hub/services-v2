package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.dto.LandscapeAssmtProjection;
import com.dbs.mot.grc.dto.LandscapeAssmtRef;
import com.dbs.mot.grc.entity.OrlLndscpAssmt;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JDBC repository for {@code orl_lndscp_assmt}.
 *
 * <ul>
 *   <li>{@code findById} (inherited) also loads the {@code details}
 *       {@code MappedCollection} declared on {@link OrlLndscpAssmt} — used by the
 *       single-assessment details endpoint, which genuinely needs the collection.</li>
 *   <li>{@link #findAllSummaries()} is a plain, single-table projection query — used by
 *       the listing endpoint, which does not need the detail rows. Returning a
 *       projection here (rather than the {@code OrlLndscpAssmt} entity) avoids
 *       Spring Data JDBC eagerly loading every assessment's detail collection just to
 *       list summaries.</li>
 * </ul>
 *
 * <p>Cross-aggregate data (e.g. the parent {@code orl_lndscp_dim} row) is always fetched
 * with a separate, equally plain call to {@link OrlLndscpDimRepository}; results are
 * combined in the service layer rather than via a hand-written SQL join.
 */
@Repository
public interface OrlLndscpAssmtRepository extends CrudRepository<OrlLndscpAssmt, Long> {

    @Query("""
            SELECT id,
                   LNDSCP_NUM    AS lndscp_num,
                   ASSEMT_PERIOD AS assmt_period,
                   status,
                   CREATED_BY    AS created_by,
                   CREATE_DT_TM  AS create_dt_tm,
                   UPDATED_BY    AS updated_by,
                   UPDATE_DT_TM  AS update_dt_tm
            FROM orl_lndscp_assmt
            """)
    List<LandscapeAssmtProjection> findAllSummaries();

    /**
     * Reference projection ({@code id} + {@code LNDSCP_NUM}) for an assessment.
     * Deliberately avoids returning the {@code OrlLndscpAssmt} entity so callers that
     * only need existence + the landscape FK do not trigger the eager
     * {@code details} {@code MappedCollection} load.
     */
    @Query("""
            SELECT id, LNDSCP_NUM AS lndscp_num
            FROM orl_lndscp_assmt
            WHERE id = :id
            """)
    Optional<LandscapeAssmtRef> findRefById(@Param("id") Long id);

    /**
     * True when an assessment already exists for the given landscape and period.
     * Backs the duplicate-generation guard (HTTP 409).
     */
    @Query("""
            SELECT COUNT(*) > 0
            FROM orl_lndscp_assmt
            WHERE LNDSCP_NUM = :lndscpNum AND ASSEMT_PERIOD = :period
            """)
    boolean existsByLndscpNumAndPeriod(@Param("lndscpNum") Long lndscpNum,
                                       @Param("period") String period);

    /**
     * Returns the assessment id for a given landscape + period, if one exists.
     * Used at generation time to locate the previous month's assessment for the
     * {@code PREV_ASSMT_NUM} self-reference.
     */
    @Query("""
            SELECT id
            FROM orl_lndscp_assmt
            WHERE LNDSCP_NUM = :lndscpNum AND ASSEMT_PERIOD = :period
            """)
    Optional<Long> findIdByLndscpNumAndPeriod(@Param("lndscpNum") Long lndscpNum,
                                              @Param("period") String period);
}
