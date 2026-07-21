package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.common.enums.NetRiskRating;
import com.dbs.mot.grc.common.enums.RiskRatingChange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RCSA module snapshot ({@code rcsa_fact_orl}), matched to an assessment dimension by
 * {@code biz_dt} + the shared dimension columns (same key as {@code fact_orl}).
 */
@Table("rcsa_fact_orl")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RcsaFactOrl implements ModuleFact {

    @Id
    @Column("ID")
    private Long id;

    // RCSA uses its own dimension/date/NRR column names (see rcsa_fact_orl schema).
    @Column("biz_date")
    private LocalDate bizDt;
    @Column("orl_risk_area")
    private String riskArea;
    @Column("orl_unit_l2")
    private String orlBuNmL2;
    @Column("orl_unit_l3")
    private String orlBuNmL3;
    @Column("orl_unit_l4")
    private String orlBuNmL4;
    @Column("orl_location")
    private String location;

    @Column("NRR")
    private NetRiskRating netRiskRtng;
    @Column("RISK_RTNG_CHGE")
    private RiskRatingChange riskRtngChge;

    @Column("combined_count_high_risk")
    private Integer combinedCountHighRisk;
    @Column("combined_count_med_high_risk")
    private Integer combinedCountMedHighRisk;
    @Column("combined_count_med_low_risk")
    private Integer combinedCountMedLowRisk;
    @Column("combined_count_low_risk")
    private Integer combinedCountLowRisk;
    @Column("rcsa_high_risk_proportion")
    private BigDecimal rcsaHighRiskProportion;
    @Column("rcsa_med_high_proportion")
    private BigDecimal rcsaMedHighProportion;
    @Column("rcsa_med_low_proportion")
    private BigDecimal rcsaMedLowProportion;
    @Column("rcsa_low_risk_proportion")
    private BigDecimal rcsaLowRiskProportion;

    @Override
    public String moduleKey() {
        return "RCSA";
    }

    @Override
    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("combined_count_high_risk", combinedCountHighRisk);
        m.put("combined_count_med_high_risk", combinedCountMedHighRisk);
        m.put("combined_count_med_low_risk", combinedCountMedLowRisk);
        m.put("combined_count_low_risk", combinedCountLowRisk);
        m.put("rcsa_high_risk_proportion", rcsaHighRiskProportion);
        m.put("rcsa_med_high_proportion", rcsaMedHighProportion);
        m.put("rcsa_med_low_proportion", rcsaMedLowProportion);
        m.put("rcsa_low_risk_proportion", rcsaLowRiskProportion);
        return m;
    }
}
