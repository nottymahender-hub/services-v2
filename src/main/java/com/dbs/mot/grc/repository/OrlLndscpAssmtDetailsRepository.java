package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.OrlLndscpAssmtDetails;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@code orl_lndscp_assmt_details} (owned by {@code OrlLndscpAssmt} as a
 * {@code @MappedCollection}); single-row reads/updates use the inherited {@code CrudRepository} methods.
 */
@Repository
public interface OrlLndscpAssmtDetailsRepository extends CrudRepository<OrlLndscpAssmtDetails, Long> {

    /**
     * The single detail row for an assessment + full dimension key (the previous month's matching row),
     * without loading its detail collection. A named {@code @Query} because it filters on the unmapped
     * {@code lndscp_assmt_id} back-reference, which no derived-query path can name.
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
