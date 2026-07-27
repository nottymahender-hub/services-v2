package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlLndscpAssmtDetails;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@code orl_lndscp_assmt_details} rows.
 *
 * <p>Detail rows are owned by {@link com.dbs.mot.grc.entity.OrlLndscpAssmt} as a
 * {@code @MappedCollection}. Single-row reads/updates go through the inherited {@code CrudRepository}
 * methods ({@code findById}, {@code save}); the overlay save uses {@code save()} on a loaded row.
 */
@Repository
public interface OrlLndscpAssmtDetailsRepository extends CrudRepository<OrlLndscpAssmtDetails, Long> {

    /**
     * The single detail row for an assessment matching a full dimension key — used to locate the
     * previous month's matching row directly, without loading the previous assessment's detail
     * collection.
     *
     * <p>This stays a named {@code @Query} (not a derived query) because it filters on
     * {@code lndscp_assmt_id}, which is the aggregate's {@code @MappedCollection} back-reference and
     * is intentionally <em>not</em> mapped as a property on the child entity — so no derived-query
     * path can name it. Parameters are bound by name (no SQL injection).
     */
    @Query("""
            SELECT * FROM orl_lndscp_assmt_details
            WHERE lndscp_assmt_id = :assmtId AND RISK_AREA = :riskArea
              AND ORL_BU_NM_L2 = :l2 AND ORL_BU_NM_L3 = :l3 AND ORL_BU_NM_L4 = :l4 AND LOCATION = :location
            """)
    Optional<OrlLndscpAssmtDetails> findByAssmtAndDimension(@Param("assmtId") Long assmtId,
                                                           @Param("riskArea") String riskArea,
                                                           @Param("l2") String l2, @Param("l3") String l3,
                                                           @Param("l4") String l4,
                                                           @Param("location") String location);
}
