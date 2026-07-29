package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.DimStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Table("orl_lndscp_dim")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlLndscpDim {

    @Id
    @Column("id")
    private Long id;

    @Column("CONFIG_ID")
    private String configId;

    @Column("LNDSCP_NM")
    private String lndscpNm;

    @Column("EFFECT_START_DT")
    private LocalDate effectStartDt;

    @Column("EFFECT_END_DT")
    private LocalDate effectEndDt;

    @Column("VERSION")
    private Integer version;

    @Column("STATUS")
    private DimStatus status;

    /** JSON map of risk area → risk type codes, e.g. {"Cyber Risk": ["OR"]}. */
    @Column("RISK_AREA")
    private String riskArea;

    @Column("BIZ_UNITS")
    private String bizUnits;

    @Column("BIZ_UNIT_LVL")
    private Integer bizUnitLvl;

    @Column("LOCATIONS")
    private String locations;

    @Column("CREATED_BY")
    private String createdBy;

    @ReadOnlyProperty
    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;

    /**
     * Whether this config row is ACTIVE and its effective window
     * ({@code EFFECT_START_DT}..{@code EFFECT_END_DT}, inclusive) contains the given date.
     * Used by bulk assessment generation to pick the config effective "today".
     */
    public boolean isActiveAndEffectiveOn(LocalDate date) {
        return status == DimStatus.ACTIVE
                && !date.isBefore(effectStartDt)
                && !date.isAfter(effectEndDt);
    }
}
