package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for POST and PUT callout APIs.
 *
 * <p>Hibernate validators enforce presence of each field; the service layer performs
 * additional semantic validation (valid risk area, location, BU values against the
 * landscape dimensions).
 */
@Getter
@NoArgsConstructor
public class CalloutRequest {

    @NotBlank(message = "RISK_AREA must not be blank.")
    private String riskArea;

    /** Location values as a string array; must contain at least one entry. */
    @NotEmpty(message = "LOCATIONS must contain at least one value.")
    private List<String> locations;

    /** Business-unit values as a string array; must contain at least one entry. */
    @NotEmpty(message = "BIZ_UNITS must contain at least one value.")
    private List<String> bizUnits;

    /** Free-text comment; required (the column is NOT NULL) and truncated to 400 characters. */
    @NotBlank(message = "comment must not be blank.")
    private String comment;

    /** SME (owner) recorded against the callout. */
    @NotBlank(message = "sme must not be blank.")
    private String sme;
}
