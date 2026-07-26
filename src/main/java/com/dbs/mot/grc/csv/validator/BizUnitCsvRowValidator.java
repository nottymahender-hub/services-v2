package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.csv.CsvRowValidator;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.BizUnitCsvRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-row validation for the BizUnit CSV batch.
 *
 * <p>Rule: {@code BU_NUM} unique across the CSV. Field values may contain literal commas
 * (e.g. {@code BU_FULL_PATH} with {@code &}-joined names), and hierarchy references
 * ({@code ORL_BU_NM_L2/L3/L4}) are not required to resolve within the same upload batch.
 */
@Component
public class BizUnitCsvRowValidator implements CsvRowValidator<BizUnitCsvRow> {

    @Override
    public List<ValidationErrorDetail> validate(List<BizUnitCsvRow> rows) {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        Map<Integer, Integer> seenBuNums = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            BizUnitCsvRow row = rows.get(i);
            int rowNum = i + 1;

            if (seenBuNums.containsKey(row.getBuNum())) {
                errors.add(error(rowNum, "BU_NUM",
                        "BU_NUM " + row.getBuNum() + " is duplicated (first seen at row "
                                + seenBuNums.get(row.getBuNum()) + ")."));
            } else {
                seenBuNums.put(row.getBuNum(), rowNum);
            }
        }
        return errors;
    }

    private ValidationErrorDetail error(int row, String field, String message) {
        return ValidationErrorDetail.builder().row(row).field(field).message(message).build();
    }
}
