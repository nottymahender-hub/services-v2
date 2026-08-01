package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.AssmtStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Entity for {@code orl_lndscp_assmt}. FKs ({@code lndscpNum}, {@code prevAssmtNum}) are one-way
 * {@link AggregateReference}s; {@code details} is a {@link MappedCollection} of the child rows,
 * eagerly loaded by {@code findById}.
 *
 * <p><b>Caution:</b> {@code save()} on an existing assessment rewrites its children (delete+reinsert),
 * so use it only for fresh inserts (generation). Prefer {@code existsById} / the {@code findHeaderById}
 * projection when the detail collection isn't needed.
 */
@Table("orl_lndscp_assmt")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlLndscpAssmt {

    @Id
    @Column("id")
    private Long id;

    /** FK → {@code orl_lndscp_dim.id}. */
    @Column("LNDSCP_NUM")
    private AggregateReference<OrlLndscpDim, Long> lndscpNum;

    @Column("ASSEMT_PERIOD")
    private String assmtPeriod;

    /**
     * Business date this assessment reports against: the previous month-end (or the latest
     * {@code fact_orl.biz_dt} within that month). Used to match {@code fact_orl}/module snapshots.
     */
    @Column("biz_dt")
    private LocalDate bizDt;

    @Column("status")
    private AssmtStatus status;

    /**
     * FK → the previous month's {@code orl_lndscp_assmt.id} for the same
     * {@code LNDSCP_NUM} (one-way self-reference); {@code null} for a landscape
     * config's first assessment.
     */
    @Column("PREV_ASSMT_NUM")
    private AggregateReference<OrlLndscpAssmt, Long> prevAssmtNum;

    @Column("CREATED_BY")
    private String createdBy;

    @ReadOnlyProperty
    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;

    @ReadOnlyProperty
    @Column("UPDATE_DT_TM")
    private LocalDateTime updateDtTm;

    @Column("UPDATED_BY")
    private String updatedBy;

    /** Child detail rows, owned by this assessment. */
    @MappedCollection(idColumn = "lndscp_assmt_id")
    private Set<OrlLndscpAssmtDetails> details;
}
