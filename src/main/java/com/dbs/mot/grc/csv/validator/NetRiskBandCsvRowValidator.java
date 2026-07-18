package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.common.csv.CsvRowValidator;
import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.NetRiskBandCsvRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-row validation for the NetRiskBand CSV batch.
 *
 * <p>Rule: composite {@code (net_risk_rtng, module)} unique per file. {@code config_version}
 * is resolved server-side (not a CSV input), so it is not part of the uniqueness check.
 */
@Component
public class NetRiskBandCsvRowValidator implements CsvRowValidator<NetRiskBandCsvRow> {

    @Override
    public List<ValidationErrorDetail> validate(List<NetRiskBandCsvRow> rows) {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        Map<String, Integer> seenKeys = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            NetRiskBandCsvRow row = rows.get(i);
            int rowNum = i + 1;

            String key = row.getModule() + "|" + row.getNetRiskRtng();
            if (seenKeys.containsKey(key)) {
                errors.add(ValidationErrorDetail.builder()
                        .row(rowNum).field("module + net_risk_rtng")
                        .message("Duplicate combination (net_risk_rtng=" + row.getNetRiskRtng()
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
