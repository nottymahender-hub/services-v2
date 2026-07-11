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
 * Entity for the {@code ina_fact_orl} table — Issues &amp; Actions module fact data.
 *
 * <p>Standalone fact table (no FK relationships). Matched to generated detail rows on
 * the shared dimension columns; module integer columns feed {@code GRC_METRICS} and
 * {@code netRiskRtng} feeds the module {@code nrr}.
 */
@Table("ina_fact_orl")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InaFactOrl {

    @Id
    @Column("id")
    private Long id;

    @Column("issue_rating_high_count")
    private Integer issueRatingHighCount;

    @Column("issue_rating_medium_count")
    private Integer issueRatingMediumCount;

    @Column("issue_type_regulatory_count")
    private Integer issueTypeRegulatoryCount;

    @Column("issue_type_audit_count")
    private Integer issueTypeAuditCount;

    @Column("issue_type_others_count")
    private Integer issueTypeOthersCount;

    @Column("issue_open_count")
    private Integer issueOpenCount;

    @Column("issue_closed_count_l3m_mtd")
    private Integer issueClosedCountL3mMtd;

    @Column("issue_repeated_count")
    private Integer issueRepeatedCount;

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
