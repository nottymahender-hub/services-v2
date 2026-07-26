package com.dbs.mot.grc.entity;

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
 * Entity for the {@code feature_score_band} table. Auto-generated BIGINT id.
 */
@Table("feature_score_band")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureScoreBand {

    @Id
    @Column("id")
    private Long id;
    @Column("config_version")
    private Integer configVersion;
    @Column("feature_bin")
    private Integer featureBin;
    @Column("feature_name")
    private String featureName;
    @Column("range_low")
    private BigDecimal rangeLow;
    @Column("range_high")
    private BigDecimal rangeHigh;
    @Column("score")
    private Integer score;
    @Column("module")
    private Module module;
    @Column("CREATED_BY")
    private String createdBy;
    @ReadOnlyProperty
    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;
}
