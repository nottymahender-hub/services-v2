package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.dto.AssmtHeader;
import com.dbs.mot.grc.dto.LandscapeAssmtProjection;
import com.dbs.mot.grc.entity.OrlLndscpAssmt;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JDBC repository for {@code orl_lndscp_assmt}. Projection queries
 * ({@link #findAllSummaries()}, {@link #findHeaderById(Long)}) avoid eagerly loading the
 * {@code details} {@code MappedCollection} that inherited {@code findById} pulls in.
 */
@Repository
public interface OrlLndscpAssmtRepository extends CrudRepository<OrlLndscpAssmt, Long> {

    /**
     * Listing projection: each assessment {@code LEFT JOIN}ed to its {@code orl_lndscp_dim} for the
     * landscape name (null when the config is missing), ordered most-recently-modified first.
     */
    @Query("""
            SELECT a.id,
                   a.LNDSCP_NUM    AS lndscp_num,
                   d.LNDSCP_NM     AS landscape_name,
                   a.ASSEMT_PERIOD AS assmt_period,
                   a.status,
                   a.CREATED_BY    AS created_by,
                   a.CREATE_DT_TM  AS create_dt_tm,
                   a.UPDATED_BY    AS updated_by,
                   a.UPDATE_DT_TM  AS update_dt_tm
            FROM orl_lndscp_assmt a
            LEFT JOIN orl_lndscp_dim d ON a.LNDSCP_NUM = d.id
            ORDER BY COALESCE(a.UPDATE_DT_TM, a.CREATE_DT_TM) DESC
            """)
    List<LandscapeAssmtProjection> findAllSummaries();

    /** Header projection for the drill-down — avoids eagerly loading the {@code details} collection. */
    @Query("""
            SELECT id,
                   LNDSCP_NUM     AS lndscp_num,
                   ASSEMT_PERIOD  AS assmt_period,
                   biz_dt         AS biz_dt,
                   PREV_ASSMT_NUM AS prev_assmt_num
            FROM orl_lndscp_assmt
            WHERE id = :id
            """)
    Optional<AssmtHeader> findHeaderById(@Param("id") Long id);

    /**
     * Whether an assessment already exists for a landscape config and reported period — the
     * duplicate-period guard used by assessment generation.
     */
    @Query("""
            SELECT COUNT(*) > 0
            FROM orl_lndscp_assmt
            WHERE LNDSCP_NUM = :lndscpNum AND ASSEMT_PERIOD = :period
            """)
    boolean existsByLndscpNumAndPeriod(@Param("lndscpNum") Long lndscpNum,
                                       @Param("period") String period);

    /**
     * The id of the assessment for a landscape config and reported period, if one exists — used by
     * generation to link a new assessment to the prior period's assessment via {@code PREV_ASSMT_NUM}.
     */
    @Query("""
            SELECT id
            FROM orl_lndscp_assmt
            WHERE LNDSCP_NUM = :lndscpNum AND ASSEMT_PERIOD = :period
            """)
    Optional<Long> findIdByLndscpNumAndPeriod(@Param("lndscpNum") Long lndscpNum,
                                              @Param("period") String period);
}
