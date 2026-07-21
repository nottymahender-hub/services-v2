package com.dbs.mot.grc.dto;

import java.time.LocalDateTime;

/**
 * Single-table projection from {@code orl_lndscp_assmt} for the listing endpoint. Used instead of
 * the {@code OrlLndscpAssmt} entity so Spring Data JDBC does not eagerly load each assessment's
 * detail {@code MappedCollection}. The {@code lastModifiedOn}/{@code lastModifiedBy} fallback is
 * derived in {@link com.dbs.mot.grc.service.LandscapeAssmtService}.
 */
public record LandscapeAssmtProjection(
        Long          id,
        Long          lndscpNum,
        String        assmtPeriod,
        String        status,
        String        createdBy,
        LocalDateTime createDtTm,
        String        updatedBy,
        LocalDateTime updateDtTm
) {}
