package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Response body for {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}} — the full
 * drill-down view of a single {@code orl_lndscp_assmt_details} row, including its
 * current-month, previous-month and live NRR snapshots.
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
 */
@Getter
@Builder
public class AssmtDetailResponse {

    /** {@code orl_lndscp_assmt_details.id}. */
    private final Long id;

    /** {@code RISK_AREA}. */
    private final String riskArea;

    /** Category-driven BU name (see class doc). */
    private final String bu;

    /** Category-driven location (see class doc). */
    private final String location;

    /** {@code STATUS}. */
    private final String status;

    /** {@code UPDATE_DT_TM}. */
    private final LocalDateTime lastModified;

    /** {@code UPDATED_BY}. */
    private final String lastModifiedBy;

    /** This month's NRR snapshot (always present). */
    private final MonthNRRDetails currentMonthNRRDetails;

    /**
     * The matched row's snapshot from the previous month's assessment
     * ({@code PREV_ASSMT_NUM}); {@code null} when there is no previous assessment
     * or no row matches this row's dimension key.
     */
    private final MonthNRRDetails prevMonthNRRDetails;

    /** Live (latest-refresh) NRR snapshot from the {@code LV_*} columns. */
    private final LiveNRRDetails liveNRRDetails;

    /** {@code COMMENTARY}. */
    private final String summary;

    /** {@code REVISED_COMMENTARY}. */
    private final String revisedSummary;

    /** {@code category}. */
    private final String category;

    /** {@code "Y"} when {@code OVRLY_NET_RISK_RTNG} is set, else {@code "N"}. */
    private final String nrrOverlaid;

    /** {@code OVRLY_JSTFKN}. */
    private final String overlayJustfkn;

    /** {@code GRC_METRICS}, parsed into a JSON tree; {@code null} when absent. */
    private final JsonNode grcMetrics;
}
