package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * Live (latest-refresh) NRR snapshot for a dimension — the {@code liveNRRDetails} block
 * of {@code GET /landscape/{lndscpAssmtId}/{assmtDetailId}}. Sourced from the
 * {@code fact_orl} row with the latest {@code biz_dt} for the row's dimension.
 *
 * <p>All fields are always serialized (null included).
 */
@Getter
@Builder
public class LiveNRRDetails {

    /** {@code fact_orl.CAL_NET_RISK_RTNG} of the latest snapshot. */
    private final String nrr;

    /** {@code fact_orl.biz_dt} of the latest snapshot. */
    private final LocalDate lastRefreshed;

    /** {@code fact_orl.CTRL_EFF_RTN} of the latest snapshot. */
    private final String ctrlEffRtn;

    /** {@code fact_orl.GRC_METRICS} of the latest snapshot, parsed into a JSON tree. */
    private final JsonNode grcMetrics;

    /** {@code fact_orl.COMMENTARY} of the latest snapshot. */
    private final String commentry;
}
