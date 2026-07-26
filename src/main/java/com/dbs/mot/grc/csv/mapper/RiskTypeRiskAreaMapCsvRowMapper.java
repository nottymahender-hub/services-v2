package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.csv.AbstractCsvRowMapper;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.RiskTypeRiskAreaMapCsvRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RiskTypeRiskAreaMapCsvRowMapper extends AbstractCsvRowMapper<RiskTypeRiskAreaMapCsvRow> {

    @Override
    public RiskTypeRiskAreaMapCsvRow map(Map<String, String> row, int rowNum,
                                         List<ValidationErrorDetail> errors) {
        String riskArea      = parseRequiredString(row, "RISK_AREA",        rowNum, errors);
        Integer riskTypeL4Num = parseRequiredInt(row,   "RISK_TYPE_L4_NUM", rowNum, errors);
        String riskTypeL4Nm  = parseRequiredString(row, "RISK_TYPE_L4_NM",  rowNum, errors);

        if (riskArea == null || riskTypeL4Num == null || riskTypeL4Nm == null) return null;

        return RiskTypeRiskAreaMapCsvRow.builder()
                .riskArea(riskArea)
                .riskTypeL4Num(riskTypeL4Num)
                .riskTypeL4Nm(riskTypeL4Nm)
                .build();
    }
}
