package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

/**
 * Entity for the {@code fact_orl} snapshot table — the source of all computed
 * assessment values (calculated NRR, rating change, control effectiveness, commentary
 * and GRC metrics).
 *
 * <p>Rows are matched to assessment detail rows by the shared dimension columns
 * ({@code RISK_AREA, ORL_BU_NM_L2/L3/L4, LOCATION}) for a given business date
 * ({@code biz_dt}). The unique index guarantees at most one row per
 * (dimension, biz_dt), so matching yields a single row.
 */
@Table("fact_orl")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FactOrl {

    @Id
    @Column("ID")
    private Long id;

    @Column("biz_dt")
    private LocalDate bizDt;

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

    /** One of {@code Minor, Moderate, Major}. */
    @Column("INHERENT_RISK")
    private String inherentRisk;

    /** One of {@code Improved, Deteriorated, Stable, N.A}. */
    @Column("RISK_RTNG_CHGE")
    private String riskRtngChge;

    /** Calculated net risk rating: one of {@code Low, Med Low, Med High, High}. */
    @Column("CAL_NET_RISK_RTNG")
    private String calNetRiskRtng;

    /** Control-effectiveness rating (free text, e.g. {@code "Satisfactory To Good"}). */
    @Column("CTRL_EFF_RTN")
    private String ctrlEffRtn;

    @Column("COMMENTARY")
    private String commentary;

    /** JSON string of GRC metrics (DB enforces json_valid). */
    @Column("GRC_METRICS")
    private String grcMetrics;
}
