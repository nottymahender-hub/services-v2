package com.dbs.mot.grc.dto;

import com.dbs.mot.grc.entity.InaFactOrl;
import com.dbs.mot.grc.entity.IncFactOrl;
import com.dbs.mot.grc.entity.KriFactOrl;
import com.dbs.mot.grc.entity.RcsaFactOrl;

/**
 * The four module fact rows that matched a single generated assessment detail row on the
 * shared dimension key ({@code biz_dt}, {@code RISK_AREA}, {@code ORL_BU_NM_L2/L3/L4},
 * {@code LOCATION}, {@code category}).
 *
 * <p>Any component may be {@code null} when that module has no fact row for the
 * combination. Passed to
 * {@link com.dbs.mot.grc.service.FactDerivationService#derive(MatchedFactRows)} so all
 * fact-derived columns are computed from a single, self-contained input.
 */
public record MatchedFactRows(
        IncFactOrl inc,
        InaFactOrl ina,
        KriFactOrl kri,
        RcsaFactOrl rcsa
) {
    /** True when at least one module matched. */
    public boolean hasAny() {
        return inc != null || ina != null || kri != null || rcsa != null;
    }
}
