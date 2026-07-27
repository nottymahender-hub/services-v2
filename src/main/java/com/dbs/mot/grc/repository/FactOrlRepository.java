package com.dbs.mot.grc.repository;

import com.dbs.mot.grc.entity.FactOrl;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@code fact_orl}. Dimension columns are {@code NOT NULL DEFAULT ''}, so the
 * dimension predicates below match on plain equality (no {@code IS NULL} handling needed).
 */
@Repository
public interface FactOrlRepository extends CrudRepository<FactOrl, Long> {

    /**
     * The {@code fact_orl} rows for one business date that match the dimension keys of a given
     * assessment's detail rows — i.e. <em>only</em> the facts the assessment actually needs, not
     * every fact for the date. Implemented as a semi-join against {@code orl_lndscp_assmt_details}
     * so the database, using the unique indexes on both tables, filters to the matching rows.
     *
     * <p>Both sides are unique on the dimension key ({@code fact_orl} on
     * {@code (biz_dt, dims)}, the detail table on {@code (lndscp_assmt_id, dims)}), so the join
     * yields at most one fact per key — no duplicates. Parameters are bound by name.
     *
     * @param lndscpAssmtId the assessment whose detail dimension keys select the facts
     * @param bizDt         the business date to match
     * @return matching {@code fact_orl} rows (empty when the assessment has no matching facts)
     */
    @Query("""
            SELECT f.* FROM fact_orl f
            JOIN orl_lndscp_assmt_details d
              ON f.RISK_AREA    = d.RISK_AREA
             AND f.ORL_BU_NM_L2 = d.ORL_BU_NM_L2
             AND f.ORL_BU_NM_L3 = d.ORL_BU_NM_L3
             AND f.ORL_BU_NM_L4 = d.ORL_BU_NM_L4
             AND f.LOCATION     = d.LOCATION
            WHERE d.lndscp_assmt_id = :lndscpAssmtId
              AND f.biz_dt = :bizDt
            """)
    List<FactOrl> findMatchingByAssmtDetails(@Param("lndscpAssmtId") Long lndscpAssmtId,
                                             @Param("bizDt") LocalDate bizDt);

    /**
     * The single snapshot row for a (business date, dimension key); at most one per the unique
     * index. Derived query — all predicate columns are mapped, equality-only (dimension columns
     * are {@code NOT NULL DEFAULT ''}).
     */
    Optional<FactOrl> findByBizDtAndRiskAreaAndOrlBuNmL2AndOrlBuNmL3AndOrlBuNmL4AndLocation(
            LocalDate bizDt, String riskArea, String orlBuNmL2, String orlBuNmL3,
            String orlBuNmL4, String location);

    /**
     * The latest business date present in {@code fact_orl}, or {@code null} when the table is empty.
     */
    @Query("SELECT MAX(biz_dt) FROM fact_orl")
    LocalDate findMaxBizDt();
}
