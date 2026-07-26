package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.csv.AbstractCsvRowMapper;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.util.ModuleConstants;
import com.dbs.mot.grc.util.RangeSentinels;
import com.dbs.mot.grc.dto.FeatureScoreBandCsvRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Maps one raw CSV row to a {@link FeatureScoreBandCsvRow}.
 * {@code range_low}/{@code range_high} may be blank, meaning an open-ended bin —
 * in that case the row is filled with {@link RangeSentinels#OPEN_LOW}/{@link RangeSentinels#OPEN_HIGH}.
 */
@Component
public class FeatureScoreBandCsvRowMapper extends AbstractCsvRowMapper<FeatureScoreBandCsvRow> {

    @Override
    public FeatureScoreBandCsvRow map(Map<String, String> row, int rowNum,
                                      List<ValidationErrorDetail> errors) {
        Integer featureBin = parseRequiredInt(row, "feature_bin", rowNum, errors);
        String featureName = parseRequiredString(row, "feature_name", rowNum, errors);
        boolean rangeLowBlank = row.getOrDefault("range_low", "").isBlank();
        boolean rangeHighBlank = row.getOrDefault("range_high", "").isBlank();
        BigDecimal rangeLow = parseOptionalDecimal(row, "range_low", rowNum, errors);
        BigDecimal rangeHigh = parseOptionalDecimal(row, "range_high", rowNum, errors);
        Integer score = parseRequiredInt(row, "score", rowNum, errors);
        String module = parseRequiredEnum(row, "module", rowNum, errors,
                ModuleConstants.VALID_MODULES);

        if (featureBin == null || featureName == null || score == null || module == null) return null;
        // parseOptionalDecimal returns null both for "blank" and "unparsable" — only substitute
        // the sentinel when the cell was genuinely blank; an unparsable cell already has an error.
        if (rangeLow == null && !rangeLowBlank) return null;
        if (rangeHigh == null && !rangeHighBlank) return null;
        if (rangeLow == null) rangeLow = RangeSentinels.OPEN_LOW;
        if (rangeHigh == null) rangeHigh = RangeSentinels.OPEN_HIGH;

        return FeatureScoreBandCsvRow.builder()
                .featureBin(featureBin).featureName(featureName)
                .rangeLow(rangeLow).rangeHigh(rangeHigh).score(score).module(module)
                .build();
    }
}
