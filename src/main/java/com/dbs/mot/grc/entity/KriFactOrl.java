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
 * Entity for the {@code kri_fact_orl} table — Key Risk Indicators module fact data.
 *
 * <p>Standalone fact table (no FK relationships). Matched to generated detail rows on
 * the shared dimension columns; module integer columns feed {@code GRC_METRICS} and
 * {@code netRiskRtng} feeds the module {@code nrr}.
 */
@Table("kri_fact_orl")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KriFactOrl {

    @Id
    @Column("id")
    private Long id;

    @Column("kri_sustained_red_3m_or_quarterly_red_count")
    private Integer kriSustainedRed3mOrQuarterlyRedCount;

    @Column("kri_sustained_red_2m_count")
    private Integer kriSustainedRed2mCount;

    @Column("kri_sustained_red_amber_4m_or_quarterly_amber_count")
    private Integer kriSustainedRedAmber4mOrQuarterlyAmberCount;

    @Column("kri_amber_sustained_red_amber_3m_count")
    private Integer kriAmberSustainedRedAmber3mCount;

    @Column("kri_red_count")
    private Integer kriRedCount;

    @Column("kri_amber_count")
    private Integer kriAmberCount;

    @Column("kri_green_count")
    private Integer kriGreenCount;

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
