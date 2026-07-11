package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.common.csv.CsvRowValidator;
import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.BuLctnHeadcountCsvRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Cross-row validation for the orl_bu_loctn_headcount CSV batch.
 * Ensures (ORL_BU_NM_L2, ORL_BU_NM_L3, ORL_BU_NM_L4, location) is unique within the batch.
 * DB-level uniqueness is enforced by the unique index; duplicate rows in the same upload
 * are caught here early.
 */
@Slf4j
@Component
public class BuLctnHeadcountCsvRowValidator implements CsvRowValidator<BuLctnHeadcountCsvRow> {

    @Override
    public List<ValidationErrorDetail> validate(List<BuLctnHeadcountCsvRow> rows) {
        log.debug("Cross-row validation for {} orl_bu_loctn_headcount row(s)", rows.size());
        List<ValidationErrorDetail> errors = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            BuLctnHeadcountCsvRow r = rows.get(i);
            String key = buildKey(r);
            if (seen.containsKey(key)) {
                errors.add(ValidationErrorDetail.builder()
                        .row(i + 1)
                        .field("ORL_BU_NM_L2/ORL_BU_NM_L3/ORL_BU_NM_L4/location")
                        .message("Duplicate combination of BU and location (first seen at row "
                                + seen.get(key) + ").")
                        .build());
            } else {
                seen.put(key, i + 1);
            }
        }

        if (errors.isEmpty()) {
            log.info("Validation passed for all {} orl_bu_loctn_headcount row(s)", rows.size());
        } else {
            log.warn("Validation failed — {} duplicate(s) found", errors.size());
        }
        return errors;
    }

    private String buildKey(BuLctnHeadcountCsvRow r) {
        return Objects.toString(r.getOrlBuNmL2(), "") + "|"
                + Objects.toString(r.getOrlBuNmL3(), "") + "|"
                + Objects.toString(r.getOrlBuNmL4(), "") + "|"
                + Objects.toString(r.getLocation(), "");
    }
}
