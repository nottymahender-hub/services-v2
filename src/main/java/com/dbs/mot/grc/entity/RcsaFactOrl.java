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
 * Entity for the {@code rcsa_fact_orl} table — RCSA module fact data.
 *
 * <p>Standalone fact table (no FK relationships). Matched to generated detail rows on
 * the shared dimension columns; module integer columns feed {@code GRC_METRICS} and
 * {@code netRiskRtng} feeds the module {@code nrr}.
 */
@Table("rcsa_fact_orl")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RcsaFactOrl {

    @Id
    @Column("id")
    private Long id;

    @Column("rcsa_high_risk_proportion")
    private Integer rcsaHighRiskProportion;

    @Column("rcsa_medhigh_risk_proportion")
    private Integer rcsaMedhighRiskProportion;

    @Column("rcsa_medlow_risk_proportion")
    private Integer rcsaMedlowRiskProportion;

    @Column("rcsa_low_risk_proportion")
    private Integer rcsaLowRiskProportion;

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
