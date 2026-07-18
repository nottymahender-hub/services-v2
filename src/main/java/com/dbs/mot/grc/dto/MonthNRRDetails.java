package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

/**
 * NRR snapshot of one assessment detail row for a specific month, used for both the
 * {@code currentMonthNRRDetails} and {@code prevMonthNRRDetails} blocks of
 * {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}}.
 *
 * <p>All fields are always serialized (null included). {@code id} is only meaningful in
 * the previous-month block and {@code revisedCommentry} only in the current-month block —
 * the other is null in each case. The rating/control/commentary values come from the
 * matching {@code fact_orl} row for that month's business date; {@code nrr} comes from
 * the assessment detail row's overlay.
 */
@Getter
@Builder
public class MonthNRRDetails {

    /** Previous-month block only: {@code prev orl_lndscp_assmt_details.id}. */
    private final Long id;

    /** {@code fact_orl.CAL_NET_RISK_RTNG}. */
    private final String nrrCalculated;

    /** {@code orl_lndscp_assmt_details.OVRLY_NET_RISK_RTNG}. */
    private final String nrr;

    /** {@code fact_orl.CTRL_EFF_RTN}. */
    private final String ctrlEffRtn;

    /** {@code orl_lndscp_assmt.ASSEMT_PERIOD}, e.g. {@code "July 2026"}. */
    private final String assmtPeriod;

    /** {@code fact_orl.GRC_METRICS}, parsed into a JSON tree. */
    private final JsonNode grcMetrics;

    /** {@code fact_orl.COMMENTARY}. */
    private final String commentry;

    /** Current-month block only: {@code orl_lndscp_assmt_details.REVISED_COMMENTARY}. */
    private final String revisedCommentry;
}
