package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Projection returned by the {@code GET /landscapes} endpoint.
 *
 * <p>Each instance represents a single row from {@code orl_lndscp_assmt}
 * enriched with the landscape name from {@code orl_lndscp_dim}.
 *
 * <ul>
 *   <li>{@code landscapeAssmtId} â€” {@code orl_lndscp_assmt.id} â€” use as path param for the details API</li>
 *   <li>{@code landscapeName}    â€” {@code orl_lndscp_dim.LNDSCP_NM}</li>
 *   <li>{@code assessmentPeriod} â€” {@code orl_lndscp_assmt.ASSEMT_PERIOD}</li>
 *   <li>{@code lastModifiedOn}   â€” {@code UPDATE_DT_TM} when set, else {@code CREATE_DT_TM}</li>
 *   <li>{@code lastModifiedBy}   â€” {@code UPDATED_BY} when set, else {@code CREATED_BY}</li>
 *   <li>{@code status}           â€” {@code orl_lndscp_assmt.status}</li>
 * </ul>
 */
@Getter
@Builder
public class LandscapeAssmtSummary {

    /** Primary key of {@code orl_lndscp_assmt}. Pass as path param to {@code GET /landscape/{id}/assessments}. */
    private final Long   landscapeAssmtId;
    private final String landscapeName;
    private final String assessmentPeriod;

    /** ISO-formatted datetime: {@code yyyy-MM-dd HH:mm:ss} */
    private final String lastModifiedOn;
    private final String lastModifiedBy;
    private final String status;
}
