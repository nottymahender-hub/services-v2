package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a single {@code orl_lndscp_callout} row.
 *
 * <p>{@code locations}/{@code bizUnits} are returned as string arrays (parsed from the
 * stored JSON). {@code sme} is the current owner; {@code lastModifiedBy} is the SME that
 * owned the callout before the most recent update.
 */
@Getter
@Builder
public class CalloutResponse {

    private final Long          id;
    private final String        riskArea;
    private final List<String>  locations;
    private final List<String>  bizUnits;
    private final String        comment;
    private final Boolean       deleted;

    /** {@code SME} â€” current owner of the callout. */
    private final String        sme;

    /** {@code CREATE_DT_TM}. */
    private final LocalDateTime createdOn;

    /** {@code UPDATE_DT_TM}; null until the callout is updated. */
    private final LocalDateTime updatedOn;

    /** {@code LAST_MODIFIED_SME} â€” SME that owned the callout before the last update. */
    private final String        lastModifiedBy;
}
