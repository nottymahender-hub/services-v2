package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * NRR snapshot of one assessment detail row for a specific month, used for both the
 * {@code currentMonthNRRDetails} and {@code prevMonthNRRDetails} blocks of
 * {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}}.
 *
 * <p>All fields are always serialized (null included). {@code id} is only meaningful in
 * the previous-month block and {@code revisedCommentry} only in the current-month block.
 * The calculated rating/control/commentary come from the matching {@code fact_orl} row for
 * that month's business date; {@code nrr}/{@code nrrOverlaid}/{@code overlayJstfkn} come from
 * the assessment detail row's overlay; {@code grcMetrics} is assembled from the per-module
 * fact tables for that business date.
 */
@Getter
@Builder
public class MonthNRRDetails {

    /** Previous-month block only: {@code prev orl_lndscp_assmt_details.id}. */
    private final Long id;

    /** {@code fact_orl.CAL_NET_RISK_RTNG} (DB value, e.g. {@code "Med Low"}). */
    private final String nrrCalculated;

    /** {@code orl_lndscp_assmt_details.OVRLY_NET_RISK_RTNG} (DB value, e.g. {@code "Med Low"}). */
    private final String nrr;

    /** {@code "Y"} when the overlay rating is set for this month, else {@code "N"}. */
    private final String nrrOverlaid;

    /** {@code orl_lndscp_assmt_details.OVRLY_JSTFKN} for this month. */
    private final String overlayJstfkn;

    /** {@code fact_orl.CTRL_EFF_RTN}. */
    private final String ctrlEffRtn;

    /** {@code orl_lndscp_assmt.ASSEMT_PERIOD}, e.g. {@code "July 2026"}. */
    private final String assmtPeriod;

    /**
     * This dimension's overall risk-rating change for the month: the previous assessment's final
     * rating for this dimension vs. this row's effective rating (overlay if set, else calculated).
     * Derived fresh on every read — never stored.
     */
    private final String riskRatingChange;

    /**
     * Per-module GRC metrics assembled from the module fact tables. All four module keys
     * ({@code RCSA}, {@code INC}, {@code INA}, {@code KRI}) are always present, and each block is
     * always fully populated — a module with no snapshot row still lists every metric with a
     * {@code null} value and {@code "N.A"} changes (see {@link GrcModuleBlock}).
     */
    private final Map<String, GrcModuleBlock> grcMetrics;

    /** {@code fact_orl.COMMENTARY}. */
    private final String commentry;

    /** Current-month block only: {@code orl_lndscp_assmt_details.REVISED_COMMENTARY}. */
    private final String revisedCommentry;

    /** {@code orl_lndscp_assmt_details.COMMENTARY_REVISED_BY} for this month's row. */
    private final String commentaryRevisedBy;

    /** {@code orl_lndscp_assmt_details.COMMENTARY_REVISED_AT} for this month's row, in Singapore time. */
    private final LocalDateTime commentaryRevisedAt;
}
