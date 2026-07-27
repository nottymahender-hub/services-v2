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
 *
 * <p>Two independent uniqueness rules are enforced within the batch:
 * <ul>
 *   <li>{@code (RISK_AREA, RISK_TYPE_L4_NUM)} must be unique (mirrors the table's unique key).</li>
 *   <li>{@code RISK_CLUSTER} must be unique for a given {@code (RISK_AREA, RISK_TYPE_L4_NUM)}:
 *       the same cluster code must not repeat within one risk-area/risk-type pair. Blank
 *       RISK_CLUSTER values are ignored (the column is optional/nullable).</li>
 * </ul>
 */
@Slf4j
@Component
public class RiskTypeRiskAreaMapCsvRowValidator implements CsvRowValidator<RiskTypeRiskAreaMapCsvRow> {

    @Override
    public List<ValidationErrorDetail> validate(List<RiskTypeRiskAreaMapCsvRow> rows) {
        log.debug("Cross-row validation for {} orl_risk_type_risk_area_map row(s)", rows.size());
        List<ValidationErrorDetail> errors = new ArrayList<>();

        // First occurrence (1-based row) of each (RISK_AREA, RISK_TYPE_L4_NUM) pair.
        Map<String, Integer> seenPair = new HashMap<>();
        // Per pair, the first row where each RISK_CLUSTER value was seen.
        Map<String, Map<String, Integer>> seenClusterByPair = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            RiskTypeRiskAreaMapCsvRow r = rows.get(i);
            int rowNum = i + 1;
            String pairKey = r.getRiskArea() + "|" + r.getRiskTypeL4Num();

            // Rule 1: unique (RISK_AREA, RISK_TYPE_L4_NUM).
            Integer firstPairRow = seenPair.putIfAbsent(pairKey, rowNum);
            if (firstPairRow != null) {
                errors.add(error(rowNum, "RISK_AREA/RISK_TYPE_L4_NUM",
                        "Duplicate (RISK_AREA, RISK_TYPE_L4_NUM) combination (first seen at row "
                                + firstPairRow + ")."));
            }

            // Rule 2: RISK_CLUSTER unique within a (RISK_AREA, RISK_TYPE_L4_NUM) pair.
            String cluster = r.getRiskCluster();
            if (cluster != null && !cluster.isBlank()) {
                Integer firstClusterRow = seenClusterByPair
                        .computeIfAbsent(pairKey, k -> new HashMap<>())
                        .putIfAbsent(cluster, rowNum);
                if (firstClusterRow != null) {
                    errors.add(error(rowNum, "RISK_CLUSTER",
                            "Duplicate RISK_CLUSTER '" + cluster + "' for (RISK_AREA, RISK_TYPE_L4_NUM)"
                                    + " (first seen at row " + firstClusterRow + ")."));
                }
            }
        }

        if (errors.isEmpty()) {
            log.info("Validation passed for all {} orl_risk_type_risk_area_map row(s)", rows.size());
        } else {
            log.warn("Validation failed for orl_risk_type_risk_area_map batch — {} error(s)", errors.size());
        }
        return errors;
    }

    private ValidationErrorDetail error(int row, String field, String message) {
        return ValidationErrorDetail.builder().row(row).field(field).message(message).build();
    }
}
