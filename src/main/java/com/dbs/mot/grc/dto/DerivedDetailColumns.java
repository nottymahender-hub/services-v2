package com.dbs.mot.grc.dto;

/**
 * The assessment-detail column values derived from the matched module fact rows.
 *
 * <p>Produced by
 * {@link com.dbs.mot.grc.service.FactDerivationService#derive(MatchedFactRows)} and
 * stamped onto each generated {@code orl_lndscp_assmt_details} row:
 * <ul>
 *   <li>{@code grcMetrics}     — JSON of per-module counts + ratings.</li>
 *   <li>{@code calNetRiskRtng} — worst NET_RISK_RTNG across the matched modules.</li>
 *   <li>{@code commentary}     — derivation pending; {@code null} for now.</li>
 *   <li>{@code ctrlEffRtn}     — derivation pending; {@code null} for now.</li>
 * </ul>
 */
public record DerivedDetailColumns(
        String grcMetrics,
        String calNetRiskRtng,
        String commentary,
        String ctrlEffRtn
) {}
