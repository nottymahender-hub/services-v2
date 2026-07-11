package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
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
    private String status;

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

    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;

    /** Status value marking a config row as usable for assessment generation. */
    private static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * Whether this config row is ACTIVE and its effective window
     * ({@code EFFECT_START_DT}..{@code EFFECT_END_DT}, inclusive) contains the given date.
     */
    public boolean isActiveAndEffectiveOn(LocalDate date) {
        return STATUS_ACTIVE.equalsIgnoreCase(status)
                && !date.isBefore(effectStartDt)
                && !date.isAfter(effectEndDt);
    }
}
