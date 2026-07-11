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
 * Entity for the {@code orl_entity_mstr} table. Client-supplied primary key.
 */
@Table("orl_entity_mstr")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlEntityMstr {

    @Id
    @Column("ENTITY_NUM")
    private Integer entityNum;
    @Column("ENTITY_NM")
    private String entityNm;
    @Column("orl_location")
    private String orlLocation;
    @Column("orl_location_ic")
    private String orlLocationIc;
    @Column("CREATED_BY")
    private String createdBy;
    @Column("CREATED_DT_TM")
    private LocalDateTime createdDtTm;
    @Column("UPDATED_BY")
    private String updatedBy;
    @Column("UPDATE_DT_TM")
    private LocalDateTime updateDtTm;
}
