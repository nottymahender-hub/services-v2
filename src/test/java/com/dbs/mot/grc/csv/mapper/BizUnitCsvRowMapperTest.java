package com.dbs.mot.grc.csv.mapper;

import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.BizUnitCsvRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BizUnitCsvRowMapperTest {

    private final BizUnitCsvRowMapper mapper = new BizUnitCsvRowMapper();

    @Test
    void map_validRow_mapsAllFields() {
        Map<String, String> row = row("2", "Tech", "2", "Tech", "", "", "");
        List<ValidationErrorDetail> errors = new ArrayList<>();
        BizUnitCsvRow result = mapper.map(row, 1, errors);

        assertThat(errors).isEmpty();
        assertThat(result).isNotNull();
        assertThat(result.getBuNum()).isEqualTo(2);
        assertThat(result.getBuNm()).isEqualTo("Tech");
        assertThat(result.getLvlOfHier()).isEqualTo(2);
        assertThat(result.getOrlBuNmL2()).isEqualTo("Tech");
        assertThat(result.getOrlBuNmL3()).isNull();
    }

    @Test
    void map_nonIntegerBuNum_addsError() {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        assertThat(mapper.map(row("ABC", "Tech", "2", "", "", "", ""), 1, errors)).isNull();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("BU_NUM");
    }

    @Test
    void map_emptyBuNm_addsError() {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        mapper.map(row("1", "", "1", "", "", "", ""), 1, errors);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("BU_NM");
    }

    @Test
    void map_emptyLvlOfHier_addsError() {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        mapper.map(row("1", "ALL", "", "", "", "", ""), 1, errors);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("LVL_OF_HIER");
    }

    @Test
    void map_multipleErrors_allCollected() {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        mapper.map(row("", "", "", "", "", "", ""), 1, errors);
        assertThat(errors).hasSizeGreaterThanOrEqualTo(2);
    }

    private Map<String, String> row(String buNum, String buNm, String lvl,
                                    String l2, String l3, String l4, String path) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("BU_NUM", buNum); m.put("BU_NM", buNm); m.put("LVL_OF_HIER", lvl);
        m.put("ORL_BU_NM_L2", l2); m.put("ORL_BU_NM_L3", l3);
        m.put("ORL_BU_NM_L4", l4); m.put("BU_FULL_PATH", path);
        return m;
    }
}
