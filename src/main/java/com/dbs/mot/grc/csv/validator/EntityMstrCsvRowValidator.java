package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.csv.CsvRowValidator;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.EntityMstrCsvRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-row validation for the EntityMstr CSV batch.
 *
 * <p>Rules: ENTITY_NUM unique, ENTITY_NM unique (case-insensitive),
 * {@code orl_location_ic} must not be {@code "IC Rollup"}.
 */
@Component
public class EntityMstrCsvRowValidator implements CsvRowValidator<EntityMstrCsvRow> {

    private static final String FORBIDDEN_LOCATION_IC = "IC Rollup";

    @Override
    public List<ValidationErrorDetail> validate(List<EntityMstrCsvRow> rows) {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        Map<Integer, Integer> seenNums = new HashMap<>();
        Map<String, Integer> seenNames = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            EntityMstrCsvRow row = rows.get(i);
            int rowNum = i + 1;

            if (seenNums.containsKey(row.getEntityNum())) {
                errors.add(error(rowNum, "ENTITY_NUM",
                        "ENTITY_NUM " + row.getEntityNum() + " is duplicated (first seen at row "
                                + seenNums.get(row.getEntityNum()) + ")."));
            } else {
                seenNums.put(row.getEntityNum(), rowNum);
            }

            String nmKey = row.getEntityNm().toLowerCase();
            if (seenNames.containsKey(nmKey)) {
                errors.add(error(rowNum, "ENTITY_NM",
                        "ENTITY_NM '" + row.getEntityNm() + "' is duplicated (first seen at row "
                                + seenNames.get(nmKey) + ")."));
            } else {
                seenNames.put(nmKey, rowNum);
            }

            if (FORBIDDEN_LOCATION_IC.equalsIgnoreCase(row.getOrlLocationIc())) {
                errors.add(error(rowNum, "orl_location_ic",
                        "orl_location_ic must not contain the value '" + FORBIDDEN_LOCATION_IC + "'."));
            }
        }
        return errors;
    }

    private ValidationErrorDetail error(int row, String field, String message) {
        return ValidationErrorDetail.builder().row(row).field(field).message(message).build();
    }
}
