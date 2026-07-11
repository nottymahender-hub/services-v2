package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.common.csv.AbstractCsvRowMapper;
import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.BizUnitCsvRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps one raw CSV row to a {@link BizUnitCsvRow}.
 * Validates required/type constraints; cross-row checks are in {@link BizUnitCsvRowValidator}.
 */
@Component
public class BizUnitCsvRowMapper extends AbstractCsvRowMapper<BizUnitCsvRow> {

    @Override
    public BizUnitCsvRow map(Map<String, String> row, int rowNum, List<ValidationErrorDetail> errors) {
        Integer buNum = parseRequiredInt(row, "BU_NUM", rowNum, errors);
        String buNm = parseRequiredString(row, "BU_NM", rowNum, errors);
        Integer lvlOfHier = parseRequiredInt(row, "LVL_OF_HIER", rowNum, errors);

        if (buNum == null || buNm == null || lvlOfHier == null) return null;

        return BizUnitCsvRow.builder()
                .buNum(buNum)
                .buNm(buNm)
                .lvlOfHier(lvlOfHier)
                .orlBuNmL2(parseOptionalString(row, "ORL_BU_NM_L2"))
                .orlBuNmL3(parseOptionalString(row, "ORL_BU_NM_L3"))
                .orlBuNmL4(parseOptionalString(row, "ORL_BU_NM_L4"))
                .buFullPath(parseOptionalString(row, "BU_FULL_PATH"))
                .build();
    }
}
