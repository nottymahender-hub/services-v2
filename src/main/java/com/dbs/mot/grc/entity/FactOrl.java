package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.common.enums.InherentRisk;
import com.dbs.mot.grc.common.enums.LevelCategory;
import com.dbs.mot.grc.common.enums.NetRiskRating;
import com.dbs.mot.grc.common.enums.RiskRatingChange;
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

    @Column("category")
    private LevelCategory category;

    @Column("INHERENT_RISK")
    private InherentRisk inherentRisk;

    @Column("RISK_RTNG_CHGE")
    private RiskRatingChange riskRtngChge;

    /** Calculated net risk rating. */
    @Column("CAL_NET_RISK_RTNG")
    private NetRiskRating calNetRiskRtng;

    /** Control-effectiveness rating (free text). */
    @Column("CTRL_EFF_RTN")
    private String ctrlEffRtn;

    @Column("COMMENTARY")
    private String commentary;
}
