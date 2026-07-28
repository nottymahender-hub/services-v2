package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response for the commentary-save endpoint: echoes the saved {@code REVISED_COMMENTARY} along with
 * the assessment and detail ids so the client can correlate the result.
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
}
