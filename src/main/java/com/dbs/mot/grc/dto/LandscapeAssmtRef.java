package com.dbs.mot.grc.dto;

/**
 * Lightweight reference projection for {@code orl_lndscp_assmt} — just the primary key
 * and the landscape FK.
 *
 * <p>Used where a caller only needs to confirm an assessment exists and read its
 * {@code LNDSCP_NUM} (e.g. callout endpoints), without triggering Spring Data JDBC's
 * eager load of the {@code details} {@code MappedCollection} that a full
 * {@code findById} on the entity would incur.
 */
public record LandscapeAssmtRef(
        Long id,
        Long lndscpNum
) {}
