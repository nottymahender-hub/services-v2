package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.csv.AbstractCsvRowMapper;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.EntityMstrCsvRow;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps one raw CSV row to an {@link EntityMstrCsvRow}.
 * Cross-row checks (duplicate ENTITY_NUM/ENTITY_NM, forbidden location IC) are in the validator.
 */
@Component
public class EntityMstrCsvRowMapper extends AbstractCsvRowMapper<EntityMstrCsvRow> {

    @Override
    public EntityMstrCsvRow map(Map<String, String> row, int rowNum, List<ValidationErrorDetail> errors) {
        Integer entityNum = parseRequiredInt(row, "ENTITY_NUM", rowNum, errors);
        String entityNm = parseRequiredString(row, "ENTITY_NM", rowNum, errors);

        if (entityNum == null || entityNm == null) return null;

        return EntityMstrCsvRow.builder()
                .entityNum(entityNum)
                .entityNm(entityNm)
                .orlLocation(parseOptionalString(row, "orl_location"))
                .orlLocationIc(parseOptionalString(row, "orl_location_ic"))
                .build();
    }
}
