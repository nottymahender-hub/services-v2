package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity for the {@code orl_entity_mstr} table.
 *
 * <p>The primary key {@code ENTITY_NUM} is client-supplied, so it is never {@code null} and
 * Spring Data JDBC cannot infer insert-vs-update from it. The upload flow therefore implements
 * {@link Persistable}: rows to insert are built with {@code newRecord=true} and rows to update
 * with {@code newRecord=false}, letting {@code saveAll(...)} batch each set correctly.
 */
@Table("orl_entity_mstr")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrlEntityMstr implements Persistable<Integer> {

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

    /** Transient insert/update marker (not persisted); see {@link Persistable}. */
    @Transient
    @Builder.Default
    private boolean newRecord = false;

    @Override
    public Integer getId() {
        return entityNum;
    }

    @Override
    public boolean isNew() {
        return newRecord;
    }
}
