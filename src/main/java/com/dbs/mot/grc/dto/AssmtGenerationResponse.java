package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response body for {@code POST /landscape/{lndscpNm}/assessments/generate} â€” a summary
 * of the assessment that was generated.
 */
@Getter
@Builder
public class AssmtGenerationResponse {

    /** Primary key of the newly created {@code orl_lndscp_assmt} row. */
    private final Long lndscpAssmtId;

    /** Landscape name ({@code orl_lndscp_dim.LNDSCP_NM}) the assessment was generated for. */
    private final String lndscpNm;

    /** {@code LNDSCP_NUM} (the {@code orl_lndscp_dim.id}) the assessment was generated for. */
    private final Long lndscpNum;

    /** Assessment period, e.g. {@code "July 2026"}. */
    private final String assmtPeriod;

    /** Number of {@code orl_lndscp_assmt_details} rows generated. */
    private final int detailRowCount;
}
