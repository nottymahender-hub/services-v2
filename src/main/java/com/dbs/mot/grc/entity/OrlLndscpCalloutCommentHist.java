package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity for the {@code orl_lndscp_callout_comment_hist} table — an append-only audit of
 * every callout comment version. One row is written on each callout create and update.
 *
 * <p>The FK to {@code orl_lndscp_callout} is modelled as a Spring Data JDBC
 * {@link AggregateReference} (one-way, no cascade, no back-navigation) — history rows are
 * never loaded as part of the callout aggregate, only inserted alongside it.
 */
@Table("orl_lndscp_callout_comment_hist")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlLndscpCalloutCommentHist {

    @Id
    @Column("id")
    private Long id;

    /** FK → {@code orl_lndscp_callout.id}. */
    @Column("callout_id")
    private AggregateReference<OrlLndscpCallout, Long> calloutId;

    /** The comment text at this version. */
    @Column("comment")
    private String comment;

    /** SME who set this comment version. */
    @Column("SME")
    private String sme;

    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;
}
