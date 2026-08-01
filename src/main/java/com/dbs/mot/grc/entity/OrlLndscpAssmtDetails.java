package com.dbs.mot.grc.entity;

import com.dbs.mot.grc.enums.DetailStatus;
import com.dbs.mot.grc.enums.LevelCategory;
import com.dbs.mot.grc.enums.NetRiskRating;
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
 * Entity for {@code orl_lndscp_assmt_details} — a thin child of {@link OrlLndscpAssmt} (owned via
 * {@link MappedCollection}, no back-reference). Carries dimension identity, the analyst overlay,
 * revised commentary and status; other computed values live in {@code fact_orl}, matched at read
 * time by dimension + business date.
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
     * User (from the request header) who last revised the commentary via the {@code /commentry} API.
     * Distinct from the generic {@code UPDATED_BY}; set only when the commentary is saved.
     */
    @Column("COMMENTARY_REVISED_BY")
    private String commentaryRevisedBy;

    /**
     * When the commentary was last revised, stored in UTC (app-written by the {@code /commentry} API,
     * so it is a normal — not {@code @ReadOnlyProperty} — column). Surfaced in Singapore time.
     */
    @Column("COMMENTARY_REVISED_AT")
    private LocalDateTime commentaryRevisedAt;

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
