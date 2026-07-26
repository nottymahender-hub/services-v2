package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The active callouts belonging to a landscape assessment, embedded in the assessment-details
 * response ({@code GET /landscape/assessments/{lndscpAssmtId}}).
 */
@Getter
@Builder
public class CalloutListResponse {

    /** Active (not soft-deleted) callouts for the assessment. */
    private final List<CalloutResponse> callouts;
}
