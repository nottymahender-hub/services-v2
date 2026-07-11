package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for POST and PUT callout APIs.
 *
 * <p>Hibernate validators enforce presence of each field; the service layer
 * performs additional semantic validation (valid risk area, location, BU values).
 */
@Getter
@NoArgsConstructor
public class CalloutRequest {

    @NotBlank(message = "RISK_AREA must not be blank.")
    private String riskArea;

    /** Comma-separated location values; must contain at least one entry. */
    @NotBlank(message = "LOCATIONS must not be blank and must contain at least one value.")
    private String locations;

    /** Comma-separated business-unit values; must contain at least one entry. */
    @NotBlank(message = "BIZ_UNITS must not be blank and must contain at least one value.")
    private String bizUnits;

    /** Optional free-text comment; truncated to 400 characters if longer. */
    private String comment;
}
