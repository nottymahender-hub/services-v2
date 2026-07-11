package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.common.csv.AbstractCsvRowMapper;
import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.common.util.ModuleConstants;
import com.dbs.mot.grc.common.util.RangeSentinels;
import com.dbs.mot.grc.dto.NetRiskBandCsvRow;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Maps one raw CSV row to a {@link NetRiskBandCsvRow}.
 * {@code range_low}/{@code range_high} may be blank (open-ended bound) — filled with
 * {@link RangeSentinels#OPEN_LOW}/{@link RangeSentinels#OPEN_HIGH} by the mapper.
 */
@Component
public class NetRiskBandCsvRowMapper extends AbstractCsvRowMapper<NetRiskBandCsvRow> {

    @Override
    public NetRiskBandCsvRow map(Map<String, String> row, int rowNum, List<ValidationErrorDetail> errors) {
        boolean rangeLowBlank = row.getOrDefault("range_low", "").isBlank();
        boolean rangeHighBlank = row.getOrDefault("range_high", "").isBlank();
        BigDecimal rangeLow = parseOptionalDecimal(row, "range_low", rowNum, errors);
        BigDecimal rangeHigh = parseOptionalDecimal(row, "range_high", rowNum, errors);
        String netRiskRtng = parseRequiredEnum(row, "net_risk_rtng", rowNum, errors,
                ModuleConstants.VALID_NET_RISK_RTNG);
        String module = parseRequiredEnum(row, "module", rowNum, errors,
                ModuleConstants.VALID_MODULES);

        if (netRiskRtng == null || module == null) return null;
        if (rangeLow == null && !rangeLowBlank) return null;
        if (rangeHigh == null && !rangeHighBlank) return null;
        if (rangeLow == null) rangeLow = RangeSentinels.OPEN_LOW;
        if (rangeHigh == null) rangeHigh = RangeSentinels.OPEN_HIGH;

        return NetRiskBandCsvRow.builder()
                .rangeLow(rangeLow).rangeHigh(rangeHigh)
                .netRiskRtng(netRiskRtng).module(module)
                .build();
    }
}
