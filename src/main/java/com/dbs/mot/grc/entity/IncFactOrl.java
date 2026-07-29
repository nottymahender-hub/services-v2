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
 * INC module snapshot ({@code inc_fact_orl}), matched to an assessment dimension by
 * {@code biz_dt} + the shared dimension columns (same key as {@code fact_orl}).
 */
@Table("inc_fact_orl")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncFactOrl implements ModuleFact {

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

    @Column("inc_is_gorc_count_l3m_mtd")
    private Integer incIsGorcCountL3mMtd;
    @Column("inc_is_min_reportable_count_l3m_mtd")
    private Integer incIsMinReportableCountL3mMtd;
    @Column("inc_is_sinp_count_l3m_mtd")
    private Integer incIsSinpCountL3mMtd;
    @Column("inc_is_mi_count_l3m_mtd")
    private Integer incIsMiCountL3mMtd;

    @Override
    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inc_is_gorc_count_l3m_mtd", incIsGorcCountL3mMtd);
        m.put("inc_is_min_reportable_count_l3m_mtd", incIsMinReportableCountL3mMtd);
        m.put("inc_is_sinp_count_l3m_mtd", incIsSinpCountL3mMtd);
        m.put("inc_is_mi_count_l3m_mtd", incIsMiCountL3mMtd);
        return m;
    }
}
