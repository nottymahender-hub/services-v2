package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.DetailStatus;
import com.dbs.mot.grc.enums.LevelCategory;
import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.enums.RiskRatingChange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Entity for the {@code orl_lndscp_assmt_details} table.
 *
 * <p>This is a child entity owned by {@link OrlLndscpAssmt} via
 * {@link MappedCollection} (see {@code OrlLndscpAssmt.details}) — it holds no
 * reference back to its parent, so the {@code orl_lndscp_assmt_id} FK relationship
 * is strictly one-way (parent → children only).
 *
 * <p><b>Thin table:</b> this row carries only its dimension identity, the analyst
 * overlay ({@code OVRLY_NET_RISK_RTNG}, {@code OVRLY_JSTFKN}), the revised commentary
 * and its status. All computed values (calculated NRR, rating change, control
 * effectiveness, commentary, GRC metrics) live in {@code fact_orl} and are matched at
 * read time by dimension + business date.
 */
@Table("orl_lndscp_assmt_details")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrlLndscpAssmtDetails {

    @Id
    @Column("id")
    private Long id;

    /** Risk area name (matches {@code riskAreas[*].riskArea} in the landscape config). */
    @Column("RISK_AREA")
    private String riskArea;

    @Column("ORL_BU_NM_L2")
    private String orlBuNmL2;

    @Column("ORL_BU_NM_L3")
    private String orlBuNmL3;

    @Column("ORL_BU_NM_L4")
    private String orlBuNmL4;

    @Column("LOCATION")
    private String location;

    @Column("category")
    private LevelCategory category;

    /** Analyst-revised commentary (distinct from the fact-sourced commentary). */
    @Column("REVISED_COMMENTARY")
    private String revisedCommentary;

    /** Analyst-overlaid net risk rating (overrides the calculated rating when set). */
    @Column("OVRLY_NET_RISK_RTNG")
    private NetRiskRating ovrlyNetRiskRtng;

    /** Justification for the overlay. */
    @Column("OVRLY_JSTFKN")
    private String ovrlyJstfkn;

    @Column("STATUS")
    private DetailStatus status;

    /**
     * Risk-rating change vs. the previous assessment for this dimension. Stored here (not derived
     * at read time): populated upstream and re-evaluated by the overlay save when the overlaid net
     * risk rating changes (see {@link com.dbs.mot.grc.util.RiskRatingChanges}).
     */
    @Column("RISK_RTNG_CHGE")
    private RiskRatingChange riskRtngChge;

    @Column("CREATED_BY")
    private String createdBy;

    @ReadOnlyProperty
    @Column("CREATE_DT_TM")
    private LocalDateTime createDtTm;

    @ReadOnlyProperty
    @Column("UPDATE_DT_TM")
    private LocalDateTime updateDtTm;

    @Column("UPDATED_BY")
    private String updatedBy;
}
