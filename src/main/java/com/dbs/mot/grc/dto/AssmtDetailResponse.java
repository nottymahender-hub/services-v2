package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response body for {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}} — the full
 * drill-down view of a single {@code orl_lndscp_assmt_details} row. The calculated
 * rating, control effectiveness, commentary and GRC metrics are nested inside the
 * per-month/live blocks (sourced from {@code fact_orl}); only the overlay, revised
 * commentary and dimension identity come from the detail row itself.
 *
 * <p>{@code bu} and {@code location} are category-driven here (unlike the list API,
 * where {@code bu} follows the landscape's configured BU level):
 * <ul>
 *   <li>{@code bu}: the hierarchy column matching the row's category
 *       ({@code L2/grp_l2 → ORL_BU_NM_L2}, {@code L3/grp_l3 → ORL_BU_NM_L3},
 *       {@code L4/grp_l4 → ORL_BU_NM_L4}), or the literal {@code "Group"} for
 *       {@code loc} rows.</li>
 *   <li>{@code location}: the literal {@code "Group"} for {@code grp_l*} rows,
 *       else the row's own {@code LOCATION}.</li>
 * </ul>
 *
 * <p>Every field is always serialized, including nulls, so the payload shape is fixed.
 */
@Getter
@Builder
public class AssmtDetailResponse {

    /** {@code orl_lndscp_assmt_details.id}. */
    private final Long id;

    /** {@code orl_lndscp_assmt.id} — the parent landscape assessment id. */
    private final Long landscapeAssessmentId;

    /** {@code orl_lndscp_assmt.LNDSCP_NUM} — the parent landscape config id. */
    private final Long landscapeId;

    /** {@code RISK_AREA}. */
    private final String riskArea;

    /** Category-driven BU name (see class doc). */
    private final String bu;

    /** Category-driven location (see class doc). */
    private final String location;

    /** {@code STATUS}. */
    private final String status;

    /** Detail's {@code UPDATE_DT_TM} when present, else {@code CREATE_DT_TM}. */
    private final LocalDateTime lastModifiedOn;

    /** Detail's {@code UPDATED_BY} when present, else {@code CREATED_BY}. */
    private final String lastModifiedBy;

    /** Latest {@code fact_orl.biz_dt} across the whole table (when data was last refreshed). */
    private final LocalDate lastRefreshed;

    /** This month's NRR snapshot (fact_orl at the assessment's business date). */
    private final MonthNRRDetails currentMonthNRRDetails;

    /**
     * The matched row's snapshot from the previous month's assessment
     * ({@code PREV_ASSMT_NUM}); {@code null} when there is no previous assessment
     * or no row matches this row's dimension key.
     */
    private final MonthNRRDetails prevMonthNRRDetails;

    /** Live (latest-refresh) NRR snapshot from {@code fact_orl}; {@code null} when none. */
    private final LiveNRRDetails liveNRRDetails;

    /** {@code category}. */
    private final String category;

    /** {@code COMMENTARY_REVISED_BY} — the user who last revised the commentary (via {@code /commentry}). */
    @io.swagger.v3.oas.annotations.media.Schema(description = "User who last revised the commentary")
    private final String commentaryRevisedBy;

    /** {@code COMMENTARY_REVISED_AT} in Singapore time — when the commentary was last revised. */
    @io.swagger.v3.oas.annotations.media.Schema(description = "When the commentary was last revised (SGT)")
    private final LocalDateTime commentaryRevisedAt;
}
