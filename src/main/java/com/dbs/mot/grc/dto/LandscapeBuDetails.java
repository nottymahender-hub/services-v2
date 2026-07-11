package com.dbs.mot.grc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LandscapeBuDetails {

    /** {@code orl_lndscp_dim.BIZ_UNIT_LVL} */
    private final Integer lvl;

    /** {@code orl_lndscp_dim.BIZ_UNITS} split on commas; omitted when null. */
    private final List<String> bizUnits;
}
