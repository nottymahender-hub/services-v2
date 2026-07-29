package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.util.Percentages;
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
 *
 * <p>The stored {@code rcsa_*_proportion} columns hold fractions (0..1); they are surfaced in the
 * API as <strong>percentages</strong> (0..100, 2 decimal places) — see {@link #metrics()}.
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
    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("combined_count_high_risk", combinedCountHighRisk);
        m.put("combined_count_med_high_risk", combinedCountMedHighRisk);
        m.put("combined_count_med_low_risk", combinedCountMedLowRisk);
        m.put("combined_count_low_risk", combinedCountLowRisk);
        // Stored proportions (0..1) are surfaced as percentages (×100, 2 dp); null stays null.
        m.put("rcsa_high_risk_proportion", Percentages.toPercentage(rcsaHighRiskProportion));
        m.put("rcsa_med_high_proportion", Percentages.toPercentage(rcsaMedHighProportion));
        m.put("rcsa_med_low_proportion", Percentages.toPercentage(rcsaMedLowProportion));
        m.put("rcsa_low_risk_proportion", Percentages.toPercentage(rcsaLowRiskProportion));
        return m;
    }
}
