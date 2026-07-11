package com.dbs.mot.grc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;

/**
 * Validated row from the {@code orl_entity_mstr} CSV upload.
 */
@Getter
@Builder
public class EntityMstrCsvRow {

    @NotNull(message = "ENTITY_NUM is required and must be a valid integer.")
    private final Integer entityNum;

    @NotBlank(message = "ENTITY_NM is required and must not be empty.")
    @Size(max = 50, message = "ENTITY_NM must not exceed 50 characters.")
    private final String entityNm;

    @Size(max = 20, message = "orl_location must not exceed 20 characters.")
    private final String orlLocation;

    @Size(max = 100, message = "orl_location_ic must not exceed 100 characters.")
    private final String orlLocationIc;
}
