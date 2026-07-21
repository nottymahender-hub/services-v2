package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Map;

/**
 * Live (latest-refresh) NRR snapshot for a dimension — the {@code liveNRRDetails} block
 * of {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}}. The rating/control/commentary
 * come from the {@code fact_orl} row with the latest {@code biz_dt} for the dimension;
 * {@code grcMetrics} is assembled from each module fact table's own latest row.
 *
 * <p>A live snapshot has no assessment overlay, so {@code nrrOverlaid} is always {@code "N"}
 * and {@code overlayJstfkn} is {@code null}. All fields are always serialized (null included).
 */
@Getter
@Builder
public class LiveNRRDetails {

    /** {@code fact_orl.CAL_NET_RISK_RTNG} of the latest snapshot (display form). */
    private final String nrr;

    /** Always {@code "N"} — a live snapshot carries no analyst overlay. */
    private final String nrrOverlaid;

    /** Always {@code null} — a live snapshot carries no overlay justification. */
    private final String overlayJstfkn;

    /** {@code fact_orl.biz_dt} of the latest snapshot. */
    private final LocalDate lastRefreshed;

    /** {@code fact_orl.CTRL_EFF_RTN} of the latest snapshot. */
    private final String ctrlEffRtn;

    /** Per-module GRC metrics assembled from each module fact table's latest row. */
    private final Map<String, Object> grcMetrics;

    /** {@code fact_orl.COMMENTARY} of the latest snapshot. */
    private final String commentry;
}
