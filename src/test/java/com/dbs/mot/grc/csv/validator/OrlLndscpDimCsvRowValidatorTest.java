package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.OrlLndscpDimCsvRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OrlLndscpDimCsvRowValidator}.
 *
 * <p>RISK_AREA is now a JSON string validated for structure; the old
 * orl_focus_area DB reference check has been removed.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrlLndscpDimCsvRowValidatorTest {

    @Autowired OrlLndscpDimCsvRowValidator validator;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_biz_unit");
        jdbc.execute("DELETE FROM orl_entity_mstr");

        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(1,'ALL',1,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(2,'Tech',2,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(3,'Ops',2,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(4,'DTI',3,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(5,'BCM',4,'seed')");

        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(1,'DBS SG','SG','seed')");
        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(2,'DBS HK','HK','seed')");
        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(3,'DBS IN','IN','seed')");
    }

    // ── Happy paths ───────────────────────────────────────────────────────────

    @Test
    void valid_allRulesPass_noErrors() {
        assertThat(validator.validate(List.of(
                row("CFG001", "Landscape A", "{\"Cyber Risk\":[\"OR\"],\"Conduct Risk\":[\"CR\"]}", "Tech,Ops", 2, "SG,HK"),
                row("CFG002", "Landscape B", "{\"Operational Risk\":[\"OR\"]}",                    "Tech",     2, "IN"),
                row("CFG003", "Landscape C", "{\"Cyber Risk\":[\"OR\"]}",                           null,       2, "SG")
        ))).isEmpty();
    }

    @Test
    void nullBizUnits_skipped_noError() {
        assertThat(validator.validate(List.of(
                row("CFG001", "Landscape A", "{\"Cyber Risk\":[\"OR\"]}", null, 2, "SG")
        ))).isEmpty();
    }

    @Test
    void validLevel3BizUnits_noError() {
        assertThat(validator.validate(List.of(
                row("CFG001", "Landscape A", "{\"Cyber Risk\":[\"OR\"]}", "DTI", 3, "SG"),
                row("CFG002", "Landscape B", "{\"Conduct Risk\":[\"CR\"]}", "DTI", 3, "HK")
        ))).isEmpty();
    }

    // ── CONFIG_ID rules ───────────────────────────────────────────────────────

    @Test
    void commaInConfigId_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001,CFG002", "Landscape A", "{\"Cyber Risk\":[\"OR\"]}", null, 2, "SG")
        ));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.getField()).isEqualTo("CONFIG_ID");
            assertThat(e.getMessage()).contains("Multiple values are not allowed");
        });
    }

    @Test
    void duplicateConfigId_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "Landscape A", "{\"Cyber Risk\":[\"OR\"]}", null, 2, "SG"),
                row("CFG001", "Landscape B", "{\"Conduct Risk\":[\"CR\"]}", null, 2, "HK")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("CONFIG_ID");
        assertThat(errors.get(0).getRow()).isEqualTo(2);
    }

    @Test
    void commaInLndscpNm_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "Land A,Land B", "{\"Cyber Risk\":[\"OR\"]}", null, 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("LNDSCP_NM");
    }

    // ── BIZ_UNIT_LVL rules ────────────────────────────────────────────────────

    @Test
    void bizUnitLvl_inconsistentAcrossRows_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[\"OR\"]}", "Tech", 2, "SG"),
                row("CFG002", "B", "{\"Conduct Risk\":[\"CR\"]}", "DTI", 3, "HK")
        ));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.getField()).isEqualTo("BIZ_UNIT_LVL");
            assertThat(e.getRow()).isEqualTo(2);
            assertThat(e.getMessage()).contains("Expected 2").contains("got 3");
        });
    }

    @Test
    void bizUnitLvl_equalToMaxLevel_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[\"OR\"]}", null, 4, "SG")
        ));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.getField()).isEqualTo("BIZ_UNIT_LVL");
            assertThat(e.getMessage()).contains("less than").contains("4");
        });
    }

    // ── RISK_AREA JSON rules ──────────────────────────────────────────────────

    @Test
    void riskArea_invalidJson_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "NOT_JSON", null, 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("RISK_AREA");
        assertThat(errors.get(0).getMessage()).contains("valid JSON");
    }

    @Test
    void riskArea_duplicateKeys_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[\"OR\"],\"Cyber Risk\":[\"LCS\"]}", null, 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("RISK_AREA");
    }

    @Test
    void riskArea_emptyArray_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[]}", null, 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("RISK_AREA");
        assertThat(errors.get(0).getMessage()).contains("non-empty array");
    }

    @Test
    void riskArea_validMultipleEntries_noError() {
        assertThat(validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[\"OR\",\"LCS\"],\"Conduct Risk\":[\"CR\"]}", null, 2, "SG")
        ))).isEmpty();
    }

    // ── BIZ_UNITS rules ───────────────────────────────────────────────────────

    @Test
    void bizUnits_unknownValue_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[\"OR\"]}", "Tech,UNKNOWN_BU", 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("BIZ_UNITS");
        assertThat(errors.get(0).getMessage()).contains("UNKNOWN_BU");
    }

    @Test
    void bizUnits_duplicateValue_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[\"OR\"]}", "Tech,Tech", 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("BIZ_UNITS");
        assertThat(errors.get(0).getMessage()).contains("Duplicate value").contains("Tech");
    }

    // ── LOCATIONS rules ───────────────────────────────────────────────────────

    @Test
    void locations_unknownValue_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[\"OR\"]}", null, 2, "SG,UNKNOWN")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("LOCATIONS");
        assertThat(errors.get(0).getMessage()).contains("UNKNOWN").contains("orl_entity_mstr.orl_location");
    }

    @Test
    void locations_duplicateValue_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[\"OR\"]}", null, 2, "SG,SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("LOCATIONS");
        assertThat(errors.get(0).getMessage()).contains("Duplicate value").contains("SG");
    }

    @Test
    void locations_allValid_noError() {
        assertThat(validator.validate(List.of(
                row("CFG001", "A", "{\"Cyber Risk\":[\"OR\"]}", null, 2, "SG,HK,IN")
        ))).isEmpty();
    }

    // ── Multi-error accumulation ──────────────────────────────────────────────

    @Test
    void multipleErrors_allReported() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "NOT_JSON", "Bad BU", 2, "BAD_LOC"),
                row("CFG001", "B", "{\"Cyber Risk\":[\"OR\"]}", "Tech", 2, "SG")
        ));
        assertThat(errors.size()).isGreaterThanOrEqualTo(3);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private OrlLndscpDimCsvRow row(String configId, String lndscpNm,
                                    String riskArea, String bizUnits,
                                    int bizUnitLvl, String locations) {
        return OrlLndscpDimCsvRow.builder()
                .configId(configId).lndscpNm(lndscpNm)
                .effectStartDt(LocalDate.of(2024, 1, 1))
                .riskArea(riskArea).bizUnits(bizUnits)
                .bizUnitLvl(bizUnitLvl).locations(locations)
                .build();
    }
}
