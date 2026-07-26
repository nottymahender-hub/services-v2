package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.dto.AssmtDetailRef;
import com.dbs.mot.grc.entity.OrlLndscpAssmtDetails;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for the {@code orl_lndscp_assmt_details} rows.
 *
 * <p>Detail rows are owned by {@link OrlLndscpAssmt} as a {@code @MappedCollection}; this
 * repository exists only for the two targeted operations the overlay-save flow needs — reading a
 * row's owning-assessment reference and applying a single-row overlay update — without loading or
 * re-saving the whole assessment aggregate.
 */
@Repository
public interface OrlLndscpAssmtDetailsRepository extends CrudRepository<OrlLndscpAssmtDetails, Long> {

    /** Owning assessment id + status for a detail row, if it exists. */
    @Query("SELECT lndscp_assmt_id, STATUS FROM orl_lndscp_assmt_details WHERE id = :id")
    Optional<AssmtDetailRef> findRefById(@Param("id") Long id);

    /**
     * The single detail row for a given id, scoped to its owning assessment. Returning empty for a
     * mismatched assessment lets the drill-down answer 404 without loading the whole aggregate.
     */
    @Query("SELECT * FROM orl_lndscp_assmt_details WHERE id = :id AND lndscp_assmt_id = :assmtId")
    Optional<OrlLndscpAssmtDetails> findByIdAndAssmt(@Param("id") Long id,
                                                     @Param("assmtId") Long assmtId);

    /**
     * The single detail row for an assessment matching a full dimension key. Used to locate the
     * previous month's matching row directly, instead of loading the previous assessment's whole
     * detail collection and filtering in memory.
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

    /** Applies the analyst overlay to a single detail row and stamps the updater. */
    @Modifying
    @Query("""
            UPDATE orl_lndscp_assmt_details
            SET REVISED_COMMENTARY = :revisedCommentary,
                OVRLY_NET_RISK_RTNG = :overlaidNrr,
                OVRLY_JSTFKN = :overlayJstfkn,
                UPDATED_BY = :updatedBy,
                UPDATE_DT_TM = :updateDtTm
            WHERE id = :id
            """)
    void saveOverlay(@Param("id") Long id,
                     @Param("revisedCommentary") String revisedCommentary,
                     @Param("overlaidNrr") String overlaidNrr,
                     @Param("overlayJstfkn") String overlayJstfkn,
                     @Param("updatedBy") String updatedBy,
                     @Param("updateDtTm") LocalDateTime updateDtTm);
}
