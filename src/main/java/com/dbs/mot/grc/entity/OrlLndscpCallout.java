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
 * Entity for the {@code orl_lndscp_callout} table.
 *
 * <p>A callout belongs to a landscape <em>assessment</em> (not directly to the landscape config).
 * The FK is modelled as a Spring Data JDBC {@link AggregateReference} (one-way, no cascade):
 * <ul>
 *   <li>{@code lndscpAssmtId} → {@code orl_lndscp_assmt.id}</li>
 * </ul>
 *
 * <p>{@code locations}/{@code bizUnits} hold JSON string arrays (e.g. {@code ["SG","HK"]}) as
 * raw text — Spring Data JDBC has no native {@code List<String>}↔JSON mapping, so the
 * conversion to/from a list happens in the service layer.
 *
 * <p>{@code sme} is the current owner of the callout; {@code lastModifiedSme} is the SME that
 * owned it before the most recent update. {@code delFlg} uses soft-delete semantics:
 * {@code true} means the row is logically deleted and is excluded from all active-callout queries.
 */
@Table("orl_lndscp_callout")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrlLndscpCallout {

    @Id
    @Column("id")
    private Long id;

    @Column("RISK_AREA")
    private String riskArea;

    /** JSON string array of location values, e.g. {@code ["SG","HK"]}. */
    @Column("LOCATIONS")
    private String locations;

    /** JSON string array of business-unit values, e.g. {@code ["Tech","Ops"]}. */
    @Column("BIZ_UNITS")
    private String bizUnits;

    /** FK → {@code orl_lndscp_assmt.id}. A callout belongs to a landscape assessment. */
    @Column("lndscp_assmt_id")
    private AggregateReference<OrlLndscpAssmt, Long> lndscpAssmtId;

    /** Free-text comment; required and capped at 400 characters before persistence. */
    @Column("comment")
    private String comment;

    /** Soft-delete flag — {@code true} means the callout is logically deleted. */
    @Column("DEL_FLG")
    private Boolean delFlg;

    /** Current SME (owner) of the callout. */
    @Column("SME")
    private String sme;

    /** SME that owned the callout before the most recent update. */
    @Column("LAST_MODIFIED_SME")
    private String lastModifiedSme;

    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;

    @Column("UPDATE_DT_TM")
    private LocalDateTime updateDtTm;
}
