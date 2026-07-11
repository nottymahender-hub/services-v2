package com.dbs.mot.grc.dto;

import java.time.LocalDateTime;

/**
 * Spring Data JDBC projection populated by
 * {@link com.dbs.mot.grc.repository.OrlLndscpAssmtRepository#findAllSummaries()}.
 *
 * <p>A plain, single-table column selection from {@code orl_lndscp_assmt} — no join,
 * no {@code CASE}/{@code COALESCE} logic. Deliberately used instead of the
 * {@code OrlLndscpAssmt} entity type for this query: the entity now carries a
 * {@code @MappedCollection} of detail rows, and returning the entity type would cause
 * Spring Data JDBC to eagerly load every assessment's full detail collection just to
 * list summaries that never use it. A projection only selects these columns, so the
 * collection is never touched.
 *
 * <ul>
 *   <li>{@code id}          — {@code orl_lndscp_assmt.id}</li>
 *   <li>{@code lndscpNum}   — {@code orl_lndscp_assmt.LNDSCP_NUM}, FK to {@code orl_lndscp_dim.id}</li>
 *   <li>{@code assmtPeriod} — {@code orl_lndscp_assmt.ASSEMT_PERIOD}</li>
 *   <li>{@code status}      — {@code orl_lndscp_assmt.status}</li>
 * </ul>
 *
 * <p>{@code lastModifiedOn}/{@code lastModifiedBy} fallback logic is computed in
 * {@link com.dbs.mot.grc.service.LandscapeAssmtService} from the raw
 * {@code createdBy}/{@code createDtTm}/{@code updatedBy}/{@code updateDtTm} fields here.
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
