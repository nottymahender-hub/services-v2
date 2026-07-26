package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.AssmtStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Entity for the {@code orl_lndscp_assmt} table.
 *
 * <p>{@code lndscpNum} is a foreign-key reference to {@code orl_lndscp_dim.id},
 * modelled as a Spring Data JDBC {@link AggregateReference} (one-way, no cascade).
 *
 * <p>{@code details} is a one-to-many composition of {@code orl_lndscp_assmt_details}
 * rows, modelled as a Spring Data JDBC {@link MappedCollection}. Loading this
 * aggregate via {@code findById} automatically fetches its detail rows in a second,
 * plain (no-join) query — the child entity holds no back-reference to this parent,
 * keeping the relationship strictly one-way.
 *
 * <p><b>Caution:</b> because {@code details} is aggregate-owned, calling
 * {@code save()} on an <em>existing</em> assessment rewrites its children
 * (delete + reinsert). Only use {@code save()} for fresh inserts (as assessment
 * generation does); update individual detail/assessment fields via targeted
 * {@code @Modifying @Query} repository methods. Callers that only need existence should use
 * {@code existsById}, and the drill-down uses the {@code findHeaderById} projection, so the
 * detail collection is loaded only by the listing endpoint that actually returns every row.
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

    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;

    @Column("UPDATE_DT_TM")
    private LocalDateTime updateDtTm;

    @Column("UPDATED_BY")
    private String updatedBy;

    /** Child detail rows, owned by this assessment. */
    @MappedCollection(idColumn = "lndscp_assmt_id")
    private Set<OrlLndscpAssmtDetails> details;
}
