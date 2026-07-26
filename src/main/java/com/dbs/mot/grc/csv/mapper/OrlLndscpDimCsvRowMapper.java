package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.csv.AbstractCsvRowMapper;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.OrlLndscpDimCsvRow;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class OrlLndscpDimCsvRowMapper extends AbstractCsvRowMapper<OrlLndscpDimCsvRow> {

    @Override
    public OrlLndscpDimCsvRow map(Map<String, String> row, int rowNum,
                                  List<ValidationErrorDetail> errors) {
        String configId    = parseRequiredString(row, "CONFIG_ID",       rowNum, errors);
        String lndscpNm    = parseRequiredString(row, "LNDSCP_NM",       rowNum, errors);
        LocalDate startDt  = parseRequiredLocalDate(row, "EFFECT_START_DT", rowNum, errors);
        LocalDate endDt    = parseRequiredLocalDate(row, "EFFECT_END_DT", rowNum, errors);
        String riskArea    = parseRequiredString(row, "RISK_AREA",        rowNum, errors);
        String bizUnits    = parseOptionalString(row, "BIZ_UNITS");
        Integer bizUnitLvl = parseRequiredInt(row, "BIZ_UNIT_LVL",       rowNum, errors);
        String locations   = parseRequiredString(row, "LOCATIONS",        rowNum, errors);

        if (configId == null || lndscpNm == null || startDt == null || endDt == null
                || riskArea == null || bizUnitLvl == null || locations == null) return null;

        return OrlLndscpDimCsvRow.builder()
                .configId(configId)
                .lndscpNm(lndscpNm)
                .effectStartDt(startDt)
                .effectEndDt(endDt)
                .riskArea(riskArea)
                .bizUnits(bizUnits)
                .bizUnitLvl(bizUnitLvl)
                .locations(locations)
                .build();
    }
}
