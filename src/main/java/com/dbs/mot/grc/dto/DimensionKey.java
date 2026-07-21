package com.dbs.mot.grc.dto;

/**
 * The shared dimension identity ({@code RISK_AREA, ORL_BU_NM_L2/L3/L4, LOCATION}) used to match
 * assessment detail rows against {@code fact_orl} rows and against prior assessments' rows.
 *
 * <p>The compact constructor normalises empty BU/location parts {@code null → ''} so that detail
 * rows (stored as {@code ''}) and fact rows always produce an equal key.
 */
public record DimensionKey(
        String riskArea,
        String orlBuNmL2,
        String orlBuNmL3,
        String orlBuNmL4,
        String location
) {
    public DimensionKey {
        orlBuNmL2 = orlBuNmL2 == null ? "" : orlBuNmL2;
        orlBuNmL3 = orlBuNmL3 == null ? "" : orlBuNmL3;
        orlBuNmL4 = orlBuNmL4 == null ? "" : orlBuNmL4;
        location  = location  == null ? "" : location;
    }
}
