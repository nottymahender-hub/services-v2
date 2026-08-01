package com.dbs.mot.grc.dto;

import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.validation.PersistableEnumValue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

/**
 * Request body for the overlay endpoint
 * {@code POST /landscape/assessment/{lndscpAssmtId}/assessmentDetail/{assmtDetailId}/overlay}.
 * Persists the analyst overlay onto an assessment detail row: the overlaid net risk rating, its
 * justification, and the revised commentary — all three in one save.
 *
 * <p>{@code overlaidNRR}/{@code overlayJstfkn} are optional but must be supplied together (enforced
 * by {@link #isOverlayPairConsistent()}). {@code revisedCommentry} is independent of that pair: a
 * blank/absent value clears the stored commentary, so a caller that wants to keep the existing
 * commentary unchanged must resend its current value.
 */
@Getter
@Setter
@NoArgsConstructor
public class SaveAssmtDetailOverlayNRRRequest {

    @Schema(description = "Overlaid net risk rating; one of Low, Med Low, Med High, High.",
            example = "Low")
    @PersistableEnumValue(enumClass = NetRiskRating.class,
            message = "overlaidNRR must be a valid net risk rating")
    private String overlaidNRR;

    @Schema(description = "Justification for the overlay (max 4000 chars).")
    @Size(max = 4000, message = "overlayJstfkn must not exceed 4000 characters")
    private String overlayJstfkn;

    @Schema(description = "Analyst-revised commentary stored in REVISED_COMMENTARY. Blank/absent clears it.",
            example = "Reviewed with the BU; risk accepted for this cycle.")
    @Size(max = 4000, message = "revisedCommentry must not exceed 4000 characters")
    private String revisedCommentry;

    /** Overlay rating and justification must be supplied together (both present or both absent). */
    @JsonIgnore
    @AssertTrue(message = "overlaidNRR and overlayJstfkn must be provided together")
    public boolean isOverlayPairConsistent() {
        return StringUtils.hasText(overlaidNRR) == StringUtils.hasText(overlayJstfkn);
    }
}
