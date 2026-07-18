package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity for the {@code orl_risk_type_risk_area_map} table.
 * {@code id} is auto-generated, so a standard {@code saveAll()} inserts new rows.
 */
@Table("orl_risk_type_risk_area_map")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlRiskTypeRiskAreaMap {

    @Id
    @Column("ID")
    private Integer id;

    /** Risk area code. */
    @Column("RISK_AREA")
    private String riskArea;

    @Column("RISK_TYPE_L4_NUM")
    private Integer riskTypeL4Num;

    @Column("RISK_TYPE_L4_NM")
    private String riskTypeL4Nm;

    @Column("CREATED_BY")
    private String createdBy;

    @Column("CREATED_DT_TM")
    private LocalDateTime createDtTm;
}
