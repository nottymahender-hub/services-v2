package com.dbs.mot.grc.dto;

import java.time.LocalDate;

/**
 * Header projection of an {@code orl_lndscp_assmt} row — the fields the single-row drill-down
 * needs without triggering the eager load of the assessment's detail {@code MappedCollection}.
 *
 * @param id           {@code orl_lndscp_assmt.id}
 * @param lndscpNum    landscape config FK ({@code LNDSCP_NUM} → {@code orl_lndscp_dim.id})
 * @param assmtPeriod  {@code ASSEMT_PERIOD}
 * @param bizDt        {@code biz_dt} — the business date used to match fact/module snapshots
 * @param prevAssmtNum previous-assessment FK ({@code PREV_ASSMT_NUM}), or {@code null}
 */
public record AssmtHeader(
        Long      id,
        Long      lndscpNum,
        String    assmtPeriod,
        LocalDate bizDt,
        Long      prevAssmtNum
) {}
