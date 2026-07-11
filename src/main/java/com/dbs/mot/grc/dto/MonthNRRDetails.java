package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * NRR snapshot of one assessment detail row for a specific month, used for both the
 * {@code currentMonthNRRDetails} and {@code prevMonthNRRDetails} blocks of
 * {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}}.
 */
@Getter
@Builder
public class MonthNRRDetails {

    /** {@code CAL_NET_RISK_RTNG} of the detail row. */
    private final String nrrCalculated;

    /** {@code OVRLY_NET_RISK_RTNG} of the detail row. */
    private final String nrr;

    /** {@code CTRL_EFF_RTN} of the detail row. */
    private final String ctrlEffRtn;

    /** {@code ASSEMT_PERIOD} of the assessment the row belongs to, e.g. {@code "July 2026"}. */
    private final String assmtPeriod;
}
