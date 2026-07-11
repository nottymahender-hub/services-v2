package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Response DTO for a single {@code orl_lndscp_callout} row.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalloutResponse {

    private final Long    id;
    private final String  riskArea;
    private final String  locations;
    private final String  bizUnits;
    private final String  comment;
    private final Boolean deleted;
}
