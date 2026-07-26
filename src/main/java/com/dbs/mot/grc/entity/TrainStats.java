package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.LevelCategory;
import com.dbs.mot.grc.enums.Module;
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
    private LevelCategory lvl;
    @Column("train_mean")
    private BigDecimal trainMean;
    @Column("train_var")
    private BigDecimal trainVar;
    @Column("module")
    private Module module;
    @Column("CREATED_BY")
    private String createdBy;
    @ReadOnlyProperty
    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;
}
