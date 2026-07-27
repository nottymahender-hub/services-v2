package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.csv.AbstractCsvRowMapper;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.RiskTypeRiskAreaMapCsvRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RiskTypeRiskAreaMapCsvRowMapper extends AbstractCsvRowMapper<RiskTypeRiskAreaMapCsvRow> {

    /** Allowed IS_OR_FA values. */
    private static final Set<String> IS_OR_FA_VALUES = Set.of("Y", "N");

    @Override
    public RiskTypeRiskAreaMapCsvRow map(Map<String, String> row, int rowNum,
                                         List<ValidationErrorDetail> errors) {
        String riskArea       = parseRequiredString(row, "RISK_AREA",        rowNum, errors);
        Integer riskTypeL4Num = parseRequiredInt(row,    "RISK_TYPE_L4_NUM", rowNum, errors);
        String riskTypeL4Nm   = parseRequiredString(row, "RISK_TYPE_L4_NM",  rowNum, errors);
        // IS_OR_FA is mandatory and restricted to Y/N; a blank or out-of-range value is rejected.
        String isOrFa         = parseRequiredEnum(row,   "IS_OR_FA", rowNum, errors, IS_OR_FA_VALUES);
        // RISK_CLUSTER is optional (nullable column); blank becomes null.
        String riskCluster    = parseOptionalString(row, "RISK_CLUSTER");

        if (riskArea == null || riskTypeL4Num == null || riskTypeL4Nm == null || isOrFa == null) {
            return null;
        }

        return RiskTypeRiskAreaMapCsvRow.builder()
                .riskArea(riskArea)
                .riskTypeL4Num(riskTypeL4Num)
                .riskTypeL4Nm(riskTypeL4Nm)
                .isOrFa(isOrFa)
                .riskCluster(riskCluster)
                .build();
    }
}
