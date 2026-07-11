package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.common.csv.AbstractCsvRowMapper;
import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.BuLctnHeadcountCsvRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class BuLctnHeadcountCsvRowMapper extends AbstractCsvRowMapper<BuLctnHeadcountCsvRow> {

    @Override
    public BuLctnHeadcountCsvRow map(Map<String, String> row, int rowNum,
                                     List<ValidationErrorDetail> errors) {
        String l2       = parseRequiredString(row, "ORL_BU_NM_L2", rowNum, errors);
        String l3       = parseOptionalString(row, "ORL_BU_NM_L3");
        String l4       = parseOptionalString(row, "ORL_BU_NM_L4");
        String location = parseRequiredString(row, "location", rowNum, errors);
        Integer headcount = parseRequiredInt(row, "headcount", rowNum, errors);

        if (l2 == null || location == null || headcount == null) return null;

        return BuLctnHeadcountCsvRow.builder()
                .orlBuNmL2(l2).orlBuNmL3(l3).orlBuNmL4(l4)
                .location(location).headcount(headcount)
                .build();
    }
}
