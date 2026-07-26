package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.csv.CsvRowValidator;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.RiskTypeRiskAreaMapCsvRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-row validation for the orl_risk_type_risk_area_map CSV batch.
 * Ensures (RISK_AREA, RISK_TYPE_L4_NUM) is unique within the batch.
 */
@Slf4j
@Component
public class RiskTypeRiskAreaMapCsvRowValidator implements CsvRowValidator<RiskTypeRiskAreaMapCsvRow> {

    @Override
    public List<ValidationErrorDetail> validate(List<RiskTypeRiskAreaMapCsvRow> rows) {
        log.debug("Cross-row validation for {} orl_risk_type_risk_area_map row(s)", rows.size());
        List<ValidationErrorDetail> errors = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            RiskTypeRiskAreaMapCsvRow r = rows.get(i);
            String key = r.getRiskArea() + "|" + r.getRiskTypeL4Num();
            if (seen.containsKey(key)) {
                errors.add(ValidationErrorDetail.builder()
                        .row(i + 1)
                        .field("RISK_AREA/RISK_TYPE_L4_NUM")
                        .message("Duplicate (RISK_AREA, RISK_TYPE_L4_NUM) combination (first seen at row "
                                + seen.get(key) + ").")
                        .build());
            } else {
                seen.put(key, i + 1);
            }
        }

        if (errors.isEmpty()) {
            log.info("Validation passed for all {} rows", rows.size());
        } else {
            log.warn("Validation failed — {} duplicate(s)", errors.size());
        }
        return errors;
    }
}
