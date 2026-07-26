package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.csv.AbstractCsvRowMapper;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.util.ModuleConstants;
import com.dbs.mot.grc.dto.TrainStatsCsvRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Maps one raw CSV row to a {@link TrainStatsCsvRow}.
 */
@Component
public class TrainStatsCsvRowMapper extends AbstractCsvRowMapper<TrainStatsCsvRow> {

    @Override
    public TrainStatsCsvRow map(Map<String, String> row, int rowNum, List<ValidationErrorDetail> errors) {
        String lvl = parseRequiredEnum(row, "lvl", rowNum, errors,
                ModuleConstants.VALID_LVL_VALUES);
        BigDecimal trainMean = parseRequiredDecimal(row, "train_mean", rowNum, errors);
        BigDecimal trainVar = parseRequiredDecimal(row, "train_var", rowNum, errors);
        String module = parseRequiredEnum(row, "module", rowNum, errors,
                ModuleConstants.VALID_MODULES);

        if (lvl == null || trainMean == null || trainVar == null || module == null)
            return null;

        return TrainStatsCsvRow.builder()
                .lvl(lvl)
                .trainMean(trainMean).trainVar(trainVar).module(module)
                .build();
    }
}
