package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Live (latest-refresh) NRR snapshot of one assessment detail row — the
 * {@code liveNRRDetails} block of {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}}.
 */
@Getter
@Builder
public class LiveNRRDetails {

    /** {@code LV_NET_RISK_RTNG}. */
    private final String nrr;

    /** {@code LV_LST_RFRSH_DT_TM}. */
    private final LocalDateTime lastRefreshed;

    /** {@code LV_CTRL_EFF_RTN}. */
    private final String ctrlEffRtn;
}
