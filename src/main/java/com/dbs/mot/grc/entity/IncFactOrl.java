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
 * Entity for the {@code inc_fact_orl} table — Incidents module fact data.
 *
 * <p>A standalone fact table with no foreign-key relationships. Rows are matched to
 * generated assessment detail rows on the shared dimension columns
 * ({@code bizDt}, {@code riskArea}, {@code orlBuNmL2/L3/L4}, {@code location}, {@code category}).
 * The module-specific integer columns are the raw metric counts surfaced in
 * {@code GRC_METRICS}; {@code netRiskRtng} feeds the module {@code nrr} and the
 * worst-of-modules calculated rating.
 */
@Table("inc_fact_orl")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncFactOrl {

    @Id
    @Column("id")
    private Long id;

    @Column("inc_is_sinp_count_l3m_mtd")
    private Integer incIsSinpCountL3mMtd;

    @Column("inc_is_mi_count_l3m_mtd")
    private Integer incIsMiCountL3mMtd;

    @Column("inc_is_gorc_count_l3m_mtd")
    private Integer incIsGorcCountL3mMtd;

    @Column("inc_is_min_reportable_count_l3m_mtd")
    private Integer incIsMinReportableCountL3mMtd;

    @Column("inc_time_to_detect_sum_l11m_mtd")
    private Integer incTimeToDetectSumL11mMtd;

    @Column("inc_count_l11m_mtd")
    private Integer incCountL11mMtd;

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

    @Column("category")
    private String category;

    @Column("NET_RISK_RTNG")
    private String netRiskRtng;
}
