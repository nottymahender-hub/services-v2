package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for POST and PUT callout APIs.
 *
 * <p>Hibernate validators enforce presence and the comment length cap; there is no further
 * semantic validation of field values against the landscape dimensions.
 */
@Getter
@NoArgsConstructor
public class CalloutRequest {

    /** Maximum stored comment length (matches the {@code comment} column width). */
    public static final int COMMENT_MAX_LEN = 400;

    @NotBlank(message = "RISK_AREA must not be blank.")
    private String riskArea;

    /** Location values as a string array; must contain at least one entry. */
    @NotEmpty(message = "LOCATIONS must contain at least one value.")
    private List<String> locations;

    /** Business-unit values as a string array; must contain at least one entry. */
    @NotEmpty(message = "BIZ_UNITS must contain at least one value.")
    private List<String> bizUnits;

    /** Free-text comment; required (the column is NOT NULL) and capped at {@value #COMMENT_MAX_LEN} characters. */
    @NotBlank(message = "comment must not be blank.")
    @Size(max = COMMENT_MAX_LEN, message = "comment must not exceed " + COMMENT_MAX_LEN + " characters.")
    private String comment;

    /** SME (owner) recorded against the callout. */
    @NotBlank(message = "sme must not be blank.")
    private String sme;
}
