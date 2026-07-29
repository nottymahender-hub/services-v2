package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.NetRiskRating;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * INA module snapshot ({@code ina_fact_orl}), matched to an assessment dimension by
 * {@code biz_dt} + the shared dimension columns (same key as {@code fact_orl}).
 */
@Table("ina_fact_orl")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InaFactOrl implements ModuleFact {

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

    @Column("NET_RISK_RATING")
    private NetRiskRating netRiskRtng;

    @Column("issue_repeated_count")
    private Integer issueRepeatedCount;
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
    @Column("residual_risk_approved_count")
    private Integer residualRiskApprovedCount;
    @Column("issue_open_count")
    private Integer issueOpenCount;
    @Column("issue_closed_count_l3m_mtd")
    private Integer issueClosedCountL3mMtd;

    @Override
    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issue_repeated_count", issueRepeatedCount);
        m.put("issue_rating_high_count", issueRatingHighCount);
        m.put("issue_rating_medium_count", issueRatingMediumCount);
        m.put("issue_type_regulatory_count", issueTypeRegulatoryCount);
        m.put("issue_type_audit_count", issueTypeAuditCount);
        m.put("issue_type_others_count", issueTypeOthersCount);
        m.put("residual_risk_approved_count", residualRiskApprovedCount);
        m.put("issue_open_count", issueOpenCount);
        m.put("issue_closed_count_l3m_mtd", issueClosedCountL3mMtd);
        return m;
    }
}
