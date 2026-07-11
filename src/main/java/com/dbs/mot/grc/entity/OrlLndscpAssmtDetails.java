package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity for the {@code orl_lndscp_assmt_details} table.
 *
 * <p>This is a child entity owned by {@link OrlLndscpAssmt} via
 * {@link MappedCollection} (see {@code OrlLndscpAssmt.details}) — it holds no
 * reference back to its parent, so the {@code orl_lndscp_assmt_id} FK relationship
 * is strictly one-way (parent → children only).
 *
 * <p>{@code calNetRiskRtng} is a plain enum value stored directly on the row
 * (e.g. {@code "Low"}, {@code "High"}) — it is no longer a foreign key to
 * {@code net_risk_band}.
 */
@Table("orl_lndscp_assmt_details")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlLndscpAssmtDetails {

    @Id
    @Column("id")
    private Long id;

    /** Risk area code, e.g. {@code "OR"} or {@code "CR"}. */
    @Column("RISK_AREA")
    private String riskArea;

    @Column("ORL_BU_NM_L2")
    private String orlBuNmL2;

    @Column("ORL_BU_NM_L3")
    private String orlBuNmL3;

    @Column("ORL_BU_NM_L4")
    private String orlBuNmL4;

    @Column("LOCATION")
    private String location;

    /** One of {@code L2, L3, L4, grp_l2, grp_l3, grp_l4, loc}. */
    @Column("category")
    private String category;

    /** One of {@code Improved, Deteriorated, Stable, N.A}. */
    @Column("RISK_RTNG_CHGE")
    private String riskRtngChge;

    /** Calculated net risk rating, stored directly (no longer an FK to {@code net_risk_band}). */
    @Column("CAL_NET_RISK_RTNG")
    private String calNetRiskRtng;

    /** Live (latest-refresh) net risk rating snapshot. */
    @Column("LV_NET_RISK_RTNG")
    private String lvNetRiskRtng;

    /** When the live snapshot was last refreshed. */
    @Column("LV_LST_RFRSH_DT_TM")
    private LocalDateTime lvLstRfrshDtTm;

    /** Live control-effectiveness rating snapshot. */
    @Column("LV_CTRL_EFF_RTN")
    private String lvCtrlEffRtn;

    @Column("COMMENTARY")
    private String commentary;

    @Column("REVISED_COMMENTARY")
    private String revisedCommentary;

    /** JSON string of GRC metrics. */
    @Column("GRC_METRICS")
    private String grcMetrics;

    @Column("RISK_SIGNAL")
    private String riskSignal;

    @Column("CTRL_EFF_RTN")
    private String ctrlEffRtn;

    @Column("OVRLY_NET_RISK_RTNG")
    private String ovrlyNetRiskRtng;

    @Column("OVRLY_JSTFKN")
    private String ovrlyJstfkn;

    @Column("STATUS")
    private String status;

    @Column("CREATED_BY")
    private String createdBy;

    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;

    @Column("UPDATE_DT_TM")
    private LocalDateTime updateDtTm;

    @Column("UPDATED_BY")
    private String updatedBy;
}
