package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.Module;
import com.dbs.mot.grc.enums.NetRiskRating;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for the {@code net_risk_band} table. Auto-generated INT id.
 */
@Table("net_risk_band")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NetRiskBand {

    @Id
    @Column("id")
    private Integer id;
    @Column("config_version")
    private Integer configVersion;
    @Column("range_low")
    private BigDecimal rangeLow;
    @Column("range_high")
    private BigDecimal rangeHigh;
    @Column("net_risk_rtng")
    private NetRiskRating netRiskRtng;
    @Column("module")
    private Module module;
    @Column("CREATED_BY")
    private String createdBy;
    @ReadOnlyProperty
    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;
}
