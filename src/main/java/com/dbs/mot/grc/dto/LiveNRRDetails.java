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

    /** {@code fact_orl.CAL_NET_RISK_RTNG} of the latest snapshot (DB value, e.g. {@code "Med Low"}). */
    private final String nrr;

    /** Always {@code "N"} — a live snapshot carries no analyst overlay. */
    private final String nrrOverlaid;

    /** Always {@code null} — a live snapshot carries no overlay justification. */
    private final String overlayJstfkn;

    /** {@code fact_orl.biz_dt} of the latest snapshot. */
    private final LocalDate lastRefreshed;

    /** {@code fact_orl.CTRL_EFF_RTN} of the latest snapshot. */
    private final String ctrlEffRtn;

    /**
     * Per-module GRC metrics assembled from each module fact table's own latest row. All four
     * module keys ({@code RCSA}, {@code INC}, {@code INA}, {@code KRI}) are always present and each
     * block is always fully populated — a module with no live row still lists every metric with a
     * {@code null} value and {@code "N.A"} changes (see {@link GrcModuleBlock}).
     */
    private final Map<String, GrcModuleBlock> grcMetrics;

    /** {@code fact_orl.COMMENTARY} of the latest snapshot. */
    private final String commentry;
}
