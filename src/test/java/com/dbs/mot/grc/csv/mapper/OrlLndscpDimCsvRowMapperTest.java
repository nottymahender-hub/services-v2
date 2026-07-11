package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.OrlLndscpDimCsvRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrlLndscpDimCsvRowMapperTest {

    private final OrlLndscpDimCsvRowMapper mapper = new OrlLndscpDimCsvRowMapper();

    private static final String VALID_RISK_AREA = "{\"Cyber Risk\":[\"OR\"],\"Conduct Risk\":[\"CR\"]}";

    @Test
    void map_allFields_mapsCorrectly() {
        Map<String, String> row = Map.of(
                "CONFIG_ID", "CFG001",
                "LNDSCP_NM", "Landscape A",
                "EFFECT_START_DT", "2024-01-15",
                "EFFECT_END_DT", "2099-12-31",
                "RISK_AREA", VALID_RISK_AREA,
                "BIZ_UNITS", "Tech,Ops",
                "BIZ_UNIT_LVL", "2",
                "LOCATIONS", "SG,HK"
        );
        List<ValidationErrorDetail> errors = new ArrayList<>();
        OrlLndscpDimCsvRow result = mapper.map(row, 1, errors);

        assertThat(errors).isEmpty();
        assertThat(result).isNotNull();
        assertThat(result.getConfigId()).isEqualTo("CFG001");
        assertThat(result.getLndscpNm()).isEqualTo("Landscape A");
        assertThat(result.getEffectStartDt()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(result.getEffectEndDt()).isEqualTo(LocalDate.of(2099, 12, 31));
        assertThat(result.getRiskArea()).isEqualTo(VALID_RISK_AREA);
        assertThat(result.getBizUnits()).isEqualTo("Tech,Ops");
        assertThat(result.getBizUnitLvl()).isEqualTo(2);
        assertThat(result.getLocations()).isEqualTo("SG,HK");
    }

    @Test
    void map_optionalBizUnitsBlank_mapsToNull() {
        Map<String, String> row = Map.of(
                "CONFIG_ID", "CFG001",
                "LNDSCP_NM", "Landscape A",
                "EFFECT_START_DT", "2024-01-01",
                "EFFECT_END_DT", "2099-12-31",
                "RISK_AREA", VALID_RISK_AREA,
                "BIZ_UNITS", "",
                "BIZ_UNIT_LVL", "2",
                "LOCATIONS", "SG"
        );
        List<ValidationErrorDetail> errors = new ArrayList<>();
        OrlLndscpDimCsvRow result = mapper.map(row, 1, errors);

        assertThat(errors).isEmpty();
        assertThat(result).isNotNull();
        assertThat(result.getBizUnits()).isNull();
    }

    @Test
    void map_missingConfigId_addsErrorAndReturnsNull() {
        Map<String, String> row = Map.of(
                "CONFIG_ID", "",
                "LNDSCP_NM", "Landscape A",
                "EFFECT_START_DT", "2024-01-01",
                "EFFECT_END_DT", "2099-12-31",
                "RISK_AREA", VALID_RISK_AREA,
                "BIZ_UNITS", "",
                "BIZ_UNIT_LVL", "2",
                "LOCATIONS", "SG"
        );
        List<ValidationErrorDetail> errors = new ArrayList<>();
        OrlLndscpDimCsvRow result = mapper.map(row, 1, errors);

        assertThat(result).isNull();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("CONFIG_ID");
    }

    @Test
    void map_missingEffectEndDt_addsErrorAndReturnsNull() {
        Map<String, String> row = Map.of(
                "CONFIG_ID", "CFG001",
                "LNDSCP_NM", "Landscape A",
                "EFFECT_START_DT", "2024-01-01",
                "EFFECT_END_DT", "",
                "RISK_AREA", VALID_RISK_AREA,
                "BIZ_UNITS", "",
                "BIZ_UNIT_LVL", "2",
                "LOCATIONS", "SG"
        );
        List<ValidationErrorDetail> errors = new ArrayList<>();
        OrlLndscpDimCsvRow result = mapper.map(row, 1, errors);

        assertThat(result).isNull();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("EFFECT_END_DT");
    }

    @Test
    void map_missingBizUnitLvl_addsErrorAndReturnsNull() {
        Map<String, String> row = Map.of(
                "CONFIG_ID", "CFG001",
                "LNDSCP_NM", "Landscape A",
                "EFFECT_START_DT", "2024-01-01",
                "EFFECT_END_DT", "2099-12-31",
                "RISK_AREA", VALID_RISK_AREA,
                "BIZ_UNITS", "",
                "BIZ_UNIT_LVL", "",
                "LOCATIONS", "SG"
        );
        List<ValidationErrorDetail> errors = new ArrayList<>();
        OrlLndscpDimCsvRow result = mapper.map(row, 1, errors);

        assertThat(result).isNull();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("BIZ_UNIT_LVL");
    }

    @Test
    void map_nonIntegerBizUnitLvl_addsErrorAndReturnsNull() {
        Map<String, String> row = Map.of(
                "CONFIG_ID", "CFG001",
                "LNDSCP_NM", "Landscape A",
                "EFFECT_START_DT", "2024-01-01",
                "EFFECT_END_DT", "2099-12-31",
                "RISK_AREA", VALID_RISK_AREA,
                "BIZ_UNITS", "",
                "BIZ_UNIT_LVL", "ABC",
                "LOCATIONS", "SG"
        );
        List<ValidationErrorDetail> errors = new ArrayList<>();
        OrlLndscpDimCsvRow result = mapper.map(row, 1, errors);

        assertThat(result).isNull();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("BIZ_UNIT_LVL");
        assertThat(errors.get(0).getMessage()).contains("valid integer");
    }

    @Test
    void map_invalidDateFormat_addsErrorAndReturnsNull() {
        Map<String, String> row = Map.of(
                "CONFIG_ID", "CFG001",
                "LNDSCP_NM", "Landscape A",
                "EFFECT_START_DT", "01/15/2024",
                "EFFECT_END_DT", "2099-12-31",
                "RISK_AREA", VALID_RISK_AREA,
                "BIZ_UNITS", "",
                "BIZ_UNIT_LVL", "2",
                "LOCATIONS", "SG"
        );
        List<ValidationErrorDetail> errors = new ArrayList<>();
        OrlLndscpDimCsvRow result = mapper.map(row, 1, errors);

        assertThat(result).isNull();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("EFFECT_START_DT");
        assertThat(errors.get(0).getMessage()).contains("yyyy-MM-dd");
    }

    @Test
    void map_multipleRequiredFieldsMissing_addsMultipleErrors() {
        Map<String, String> row = Map.of(
                "CONFIG_ID", "",
                "LNDSCP_NM", "",
                "EFFECT_START_DT", "",
                "EFFECT_END_DT", "",
                "RISK_AREA", "",
                "BIZ_UNITS", "",
                "BIZ_UNIT_LVL", "",
                "LOCATIONS", ""
        );
        List<ValidationErrorDetail> errors = new ArrayList<>();
        OrlLndscpDimCsvRow result = mapper.map(row, 1, errors);

        assertThat(result).isNull();
        assertThat(errors.size()).isGreaterThanOrEqualTo(6);
    }
}
