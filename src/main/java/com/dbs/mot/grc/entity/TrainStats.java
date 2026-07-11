package com.dbs.mot.grc.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for the {@code train_stats} table. Auto-generated INT id.
 */
@Table("train_stats")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainStats {

    @Id
    @Column("id")
    private Integer id;
    @Column("config_version")
    private Integer configVersion;
    @Column("lvl")
    private String lvl;
    @Column("train_mean")
    private BigDecimal trainMean;
    @Column("train_var")
    private BigDecimal trainVar;
    @Column("module")
    private String module;
    @Column("CREATED_BY")
    private String createdBy;
    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;
}
