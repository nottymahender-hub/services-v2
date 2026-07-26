package com.dbs.mot.grc.dto;

import java.time.LocalDateTime;

/**
 * Projection for the listing endpoint: {@code orl_lndscp_assmt} joined with its parent
 * {@code orl_lndscp_dim} to carry the landscape name directly. Used instead of the
 * {@code OrlLndscpAssmt} entity so Spring Data JDBC does not eagerly load each assessment's detail
 * {@code MappedCollection}, and instead of a second full load of every landscape config. The
 * {@code lastModifiedOn}/{@code lastModifiedBy} fallback is derived in
 * {@link com.dbs.mot.grc.service.LandscapeAssmtService}.
 */
public record LandscapeAssmtProjection(
        Long          id,
        Long          lndscpNum,
        String        landscapeName,
        String        assmtPeriod,
        String        status,
        String        createdBy,
        LocalDateTime createDtTm,
        String        updatedBy,
        LocalDateTime updateDtTm
) {}
