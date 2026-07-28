package com.dbs.mot.grc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for {@code POST /landscape/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}/commentry}.
 * Updates only the analyst-revised commentary ({@code REVISED_COMMENTARY}) of a detail row.
 *
 * <p>The value is optional: a blank/absent value clears the stored commentary (stored as
 * {@code null}), mirroring how the overlay save treats its revised-commentary field.
 */
@Getter
@Setter
@NoArgsConstructor
public class SaveCommentaryRequest {

    @Schema(description = "Analyst-revised commentary stored in REVISED_COMMENTARY. Blank clears it.",
            example = "Reviewed with the BU; risk accepted for this cycle.")
    private String revisedCommentry;
}
