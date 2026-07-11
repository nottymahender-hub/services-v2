package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entity for the {@code orl_lndscp_callout} table.
 *
 * <p>A callout belongs to a landscape <em>assessment</em> (not directly to the landscape config).
 * The FK is modelled as a Spring Data JDBC {@link AggregateReference} (one-way, no cascade):
 * <ul>
 *   <li>{@code lndscpAssmtId} → {@code orl_lndscp_assmt.id}</li>
 * </ul>
 *
 * <p>{@code delFlg} uses soft-delete semantics: {@code true} means the row is logically
 * deleted and is excluded from all active-callout queries.
 */
@Table("orl_lndscp_callout")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlLndscpCallout {

    @Id
    @Column("id")
    private Long id;

    @Column("RISK_AREA")
    private String riskArea;

    /** Comma-separated location values, e.g. {@code "SG,HK"}. */
    @Column("LOCATIONS")
    private String locations;

    /** Comma-separated business-unit values, e.g. {@code "Tech,Ops"}. */
    @Column("BIZ_UNITS")
    private String bizUnits;

    /** FK → {@code orl_lndscp_assmt.id}. A callout belongs to a landscape assessment. */
    @Column("lndscp_assmt_id")
    private AggregateReference<OrlLndscpAssmt, Long> lndscpAssmtId;

    /** Optional free-text comment; capped at 400 characters before persistence. */
    @Column("comment")
    private String comment;

    /** Soft-delete flag — {@code true} means the callout is logically deleted. */
    @Column("DEL_FLG")
    private Boolean delFlg;
}
