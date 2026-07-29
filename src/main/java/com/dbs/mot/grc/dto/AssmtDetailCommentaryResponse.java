package com.dbs.mot.grc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Response for the commentary-save endpoint: echoes the saved {@code REVISED_COMMENTARY} plus who
 * revised it and when, along with the assessment and detail ids so the client can correlate the
 * result.
 *
 * @see com.dbs.mot.grc.dto.SaveCommentaryRequest
 */
@Getter
@Builder
public class AssmtDetailCommentaryResponse {

    /** {@code orl_lndscp_assmt.id} the detail belongs to (from the request path). */
    private final Long lndscpAssmtId;

    /** {@code orl_lndscp_assmt_details.id} that was updated. */
    private final Long assmtDetailId;

    /** The saved {@code REVISED_COMMENTARY} value ({@code null} when cleared). */
    private final String revisedCommentary;

    /** {@code COMMENTARY_REVISED_BY} — the user (request header) who saved this revision. */
    @Schema(description = "User who revised the commentary")
    private final String commentaryRevisedBy;

    /** {@code COMMENTARY_REVISED_AT} in Singapore time — when this revision was saved. */
    @Schema(description = "When the commentary was revised (SGT)")
    private final LocalDateTime commentaryRevisedAt;
}
