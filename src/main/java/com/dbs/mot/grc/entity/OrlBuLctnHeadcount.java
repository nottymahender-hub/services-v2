package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity for the {@code orl_bu_loctn_headcount} table.
 * {@code id} is auto-generated; the import service upserts on the unique index
 * {@code (ORL_BU_NM_L2, ORL_BU_NM_L3, ORL_BU_NM_L4, location)} by matching existing rows and
 * reusing their id (see {@code BuLocationHeadcountConfigImportService}).
 */
@Table("orl_bu_loctn_headcount")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlBuLctnHeadcount {

    @Id
    @Column("id")
    private Long id;

    @Column("ORL_BU_NM_L2")
    private String orlBuNmL2;

    @Column("ORL_BU_NM_L3")
    private String orlBuNmL3;

    @Column("ORL_BU_NM_L4")
    private String orlBuNmL4;

    @Column("location")
    private String location;

    @Column("headcount")
    private Integer headcount;

    @ReadOnlyProperty
    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;

    @Column("CREATED_BY")
    private String createdBy;

    @ReadOnlyProperty
    @Column("UPDATE_DT_TM")
    private LocalDateTime updateDtTm;

    @Column("UPDATED_BY")
    private String updatedBy;
}
