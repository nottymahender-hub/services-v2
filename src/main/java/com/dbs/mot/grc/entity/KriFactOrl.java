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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KRI module snapshot ({@code kri_fact_orl}), matched to an assessment dimension by
 * {@code biz_dt} + the shared dimension columns (same key as {@code fact_orl}).
 *
 * <p>{@code KRI_RED_PROP} and {@code KRI_GREEN_PROP} are not stored columns — they are derived
 * from the red/green counts over the active count (see {@link #metrics()}).
 */
@Table("kri_fact_orl")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KriFactOrl implements ModuleFact {

    /** Scale used for the derived KRI proportions. */
    private static final int PROPORTION_SCALE = 6;

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

    @Column("NET_RISK_RTNG")
    private NetRiskRating netRiskRtng;
    @Column("RISK_RTNG_CHGE")
    private RiskRatingChange riskRtngChge;

    @Column("KRI_ACTIVE_CNT")
    private Integer kriActiveCnt;
    @Column("KRI_SUSTND_RED_3M_OR_QTRLY_RED_CNT")
    private Integer kriSustndRed3mOrQtrlyRedCnt;
    @Column("KRI_SUSTND_RED_2M_CNT")
    private Integer kriSustndRed2mCnt;
    @Column("KRI_SUSTND_RED_AMBER_4M_OR_QTRLY_AMBER_CNT")
    private Integer kriSustndRedAmber4mOrQtrlyAmberCnt;
    @Column("KRI_AMBER_SUSTND_RED_AMBER_3M_CNT")
    private Integer kriAmberSustndRedAmber3mCnt;
    @Column("KRI_RED_CNT")
    private Integer kriRedCnt;
    @Column("KRI_AMBER_CNT")
    private Integer kriAmberCnt;
    @Column("KRI_GREEN_CNT")
    private Integer kriGreenCnt;

    @Override
    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("KRI_ACTIVE_CNT", kriActiveCnt);
        m.put("KRI_SUSTND_RED_3M_OR_QTRLY_RED_CNT", kriSustndRed3mOrQtrlyRedCnt);
        m.put("KRI_SUSTND_RED_2M_CNT", kriSustndRed2mCnt);
        m.put("KRI_SUSTND_RED_AMBER_4M_OR_QTRLY_AMBER_CNT", kriSustndRedAmber4mOrQtrlyAmberCnt);
        m.put("KRI_AMBER_SUSTND_RED_AMBER_3M_CNT", kriAmberSustndRedAmber3mCnt);
        m.put("KRI_RED_CNT", kriRedCnt);
        m.put("KRI_AMBER_CNT", kriAmberCnt);
        m.put("KRI_GREEN_CNT", kriGreenCnt);
        m.put("KRI_RED_PROP", proportion(kriRedCnt, kriActiveCnt));
        m.put("KRI_GREEN_PROP", proportion(kriGreenCnt, kriActiveCnt));
        return m;
    }

    /** {@code count / activeCount}, or {@code null} when either is null or the active count is 0. */
    private static BigDecimal proportion(Integer count, Integer activeCount) {
        if (count == null || activeCount == null || activeCount == 0) {
            return null;
        }
        return BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(activeCount), PROPORTION_SCALE, RoundingMode.HALF_UP);
    }
}
