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
 * Persists the analyst overlay onto an assessment detail row: the overlaid net risk rating and its
 * justification. (Revised commentary is saved separately via the {@code /commentry} API.)
 *
 * <p>Both fields are optional, but when {@code overlaidNRR} is provided {@code overlayJstfkn} is
 * required — and vice versa (enforced by {@link #isOverlayPairConsistent()}).
 */
@Getter
@Setter
@NoArgsConstructor
public class SaveAssmtDetailRequest {

    @Schema(description = "Overlaid net risk rating; one of Low, Med Low, Med High, High.",
            example = "Low")
    @PersistableEnumValue(enumClass = NetRiskRating.class,
            message = "overlaidNRR must be a valid net risk rating")
    private String overlaidNRR;

    @Schema(description = "Justification for the overlay (max 4000 chars).")
    @Size(max = 4000, message = "overlayJstfkn must not exceed 4000 characters")
    private String overlayJstfkn;

    /** Overlay rating and justification must be supplied together (both present or both absent). */
    @JsonIgnore
    @AssertTrue(message = "overlaidNRR and overlayJstfkn must be provided together")
    public boolean isOverlayPairConsistent() {
        return StringUtils.hasText(overlaidNRR) == StringUtils.hasText(overlayJstfkn);
    }
}
