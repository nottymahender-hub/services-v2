package com.dbs.mot.grc.dto;

/**
 * The shared dimension identity of an assessment detail row / {@code fact_orl} row, used
 * as an in-memory map key to match detail rows against fact rows and against the previous
 * assessment's rows.
 *
 * <p>The five parts mirror the unique index on both {@code orl_lndscp_assmt_details} and
 * {@code fact_orl} ({@code RISK_AREA, ORL_BU_NM_L2/L3/L4, LOCATION}). {@code category} is
 * intentionally NOT part of the key — it is derivable from the dimension columns and is not
 * part of either unique index. {@code biz_dt} is also excluded: fact rows are fetched
 * already filtered to the target date, so it never varies within a match set.
 *
 * <p>Empty BU/location dimensions are stored as {@code ''} in the assessment detail table
 * but may still arrive as {@code null} from external {@code fact_orl} rows. The compact
 * constructor normalises {@code null → ''} so both representations produce an equal key —
 * this is what lets a detail row and its matching fact row line up regardless of which
 * empty-marker each side used.
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
