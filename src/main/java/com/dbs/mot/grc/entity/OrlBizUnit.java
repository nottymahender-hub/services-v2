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
 * Entity for the {@code orl_biz_unit} table.
 * BU_NUM is a client-supplied primary key; use {@code JdbcAggregateTemplate.insert()}
 * rather than {@code save()} to avoid the UPDATE-first behaviour.
 */
@Table("orl_biz_unit")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlBizUnit {

    @Id
    @Column("BU_NUM")
    private Integer buNum;
    @Column("BU_NM")
    private String buNm;
    @Column("LVL_OF_HIER")
    private Integer lvlOfHier;
    @Column("ORL_BU_NM_L2")
    private String orlBuNmL2;
    @Column("ORL_BU_NM_L3")
    private String orlBuNmL3;
    @Column("ORL_BU_NM_L4")
    private String orlBuNmL4;
    @Column("BU_FULL_PATH")
    private String buFullPath;
    @Column("CREATED_BY")
    private String createdBy;
    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;
    @Column("UPDATED_BY")
    private String updatedBy;
    @Column("UPDATE_DT_TM")
    private LocalDateTime updateDtTm;
}
