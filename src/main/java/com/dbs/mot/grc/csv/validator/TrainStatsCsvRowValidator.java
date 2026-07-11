package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.common.csv.CsvRowValidator;
import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.TrainStatsCsvRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-row validation for the TrainStats CSV batch.
 *
 * <p>Rule: composite (lvl, module) unique per file — config_version is no longer a CSV
 * input, it is resolved server-side, so uniqueness is checked without it.
 */
@Component
public class TrainStatsCsvRowValidator implements CsvRowValidator<TrainStatsCsvRow> {

    @Override
    public List<ValidationErrorDetail> validate(List<TrainStatsCsvRow> rows) {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        Map<String, Integer> seenKeys = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            TrainStatsCsvRow row = rows.get(i);
            int rowNum = i + 1;

            String key = row.getLvl() + "|" + row.getModule();
            if (seenKeys.containsKey(key)) {
                errors.add(ValidationErrorDetail.builder()
                        .row(rowNum).field("lvl + module")
                        .message("Duplicate combination (lvl=" + row.getLvl()
                                + ", module=" + row.getModule()
                                + ") — already defined in row " + seenKeys.get(key) + ".")
                        .build());
            } else {
                seenKeys.put(key, rowNum);
            }
        }
        return errors;
    }
}
