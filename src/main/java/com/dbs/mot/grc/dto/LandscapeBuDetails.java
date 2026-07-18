package com.dbs.mot.grc.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Nested DTO that groups the BU hierarchy level with its BU name list,
 * used inside {@link LandscapeDimensions}.
 *
 * <pre>
 * "buDetails": {
 *   "lvl"     : 2,
 *   "bizUnits": ["CBG", "IBG"]
 * }
 * </pre>
 *
 * <p>Null properties are serialized (no {@code NON_NULL} filtering).
 */
@Getter
@Builder
public class LandscapeBuDetails {

    /** {@code orl_lndscp_dim.BIZ_UNIT_LVL} */
    private final Integer lvl;

    /** {@code orl_lndscp_dim.BIZ_UNITS} split on commas; null when the config has none. */
    private final List<String> bizUnits;
}
