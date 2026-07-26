package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.dto.ValidationErrorDetail;
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
 * <p>RISK_AREA is a grouped JSON array validated for structure (see
 * {@link com.dbs.mot.grc.util.RiskAreaParser}); risk area names must be unique
 * across the document and each must map to a non-empty list of risk clusters.
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
                row("CFG001", "Landscape A", ra("Cyber Risk", "Conduct Risk"), "Tech,Ops", 2, "SG,HK"),
                row("CFG002", "Landscape B", ra("Operational Risk"),          "Tech",     2, "IN"),
                row("CFG003", "Landscape C", ra("Cyber Risk"),                 null,       2, "SG")
        ))).isEmpty();
    }

    @Test
    void nullBizUnits_skipped_noError() {
        assertThat(validator.validate(List.of(
                row("CFG001", "Landscape A", ra("Cyber Risk"), null, 2, "SG")
        ))).isEmpty();
    }

    @Test
    void validLevel3BizUnits_noError() {
        assertThat(validator.validate(List.of(
                row("CFG001", "Landscape A", ra("Cyber Risk"),   "DTI", 3, "SG"),
                row("CFG002", "Landscape B", ra("Conduct Risk"), "DTI", 3, "HK")
        ))).isEmpty();
    }

    // ── Non-RISK_AREA rules ─────────────────────────────────────────────────────

    @Test
    void commaInConfigId_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001,CFG002", "Landscape A", ra("Cyber Risk"), null, 2, "SG")
        ));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.getField()).isEqualTo("CONFIG_ID");
            assertThat(e.getMessage()).contains("Multiple values");
        });
    }

    @Test
    void duplicateConfigId_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "Landscape A", ra("Cyber Risk"),   null, 2, "SG"),
                row("CFG001", "Landscape B", ra("Conduct Risk"), null, 2, "HK")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("CONFIG_ID");
    }

    @Test
    void commaInLndscpNm_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "Land A,Land B", ra("Cyber Risk"), null, 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("LNDSCP_NM");
    }

    @Test
    void bizUnitLvl_inconsistentAcrossRows_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", ra("Cyber Risk"),   "Tech", 2, "SG"),
                row("CFG002", "B", ra("Conduct Risk"), "DTI",  3, "HK")
        ));
        assertThat(errors).anySatisfy(e ->
                assertThat(e.getField()).isEqualTo("BIZ_UNIT_LVL"));
    }

    @Test
    void bizUnitLvl_equalToMaxLevel_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", ra("Cyber Risk"), null, 4, "SG")
        ));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.getField()).isEqualTo("BIZ_UNIT_LVL");
            assertThat(e.getMessage()).contains("maximum hierarchy level");
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
        String dup = "[{\"groupName\":\"IT\",\"groupName\":\"Data\",\"isGroup\":true,"
                + "\"riskAreas\":[{\"riskArea\":\"Cyber Risk\",\"riskClusters\":[\"OR\"]}]}]";
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", dup, null, 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("RISK_AREA");
    }

    @Test
    void riskArea_emptyClusters_addsError() {
        String json = "[{\"groupName\":\"IT\",\"isGroup\":true,"
                + "\"riskAreas\":[{\"riskArea\":\"Cyber Risk\",\"riskClusters\":[]}]}]";
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", json, null, 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("RISK_AREA");
        assertThat(errors.get(0).getMessage()).contains("risk clusters");
    }

    @Test
    void riskArea_groupedMissingGroupName_addsError() {
        // isGroup=true requires a non-empty groupName.
        String json = "[{\"isGroup\":true,"
                + "\"riskAreas\":[{\"riskArea\":\"Cyber Risk\",\"riskClusters\":[\"OR\"]}]}]";
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", json, null, 2, "SG")
        ));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.getField()).isEqualTo("RISK_AREA");
            assertThat(e.getMessage()).contains("groupName");
        });
    }

    @Test
    void riskArea_standaloneBlankGroupName_noError() {
        // isGroup=false may leave groupName blank.
        String json = "[{\"groupName\":\"\",\"isGroup\":false,"
                + "\"riskAreas\":[{\"riskArea\":\"Transaction Capture and Execution\",\"riskClusters\":[\"OR\"]}]}]";
        assertThat(validator.validate(List.of(
                row("CFG001", "A", json, null, 2, "SG")
        ))).isEmpty();
    }

    @Test
    void riskArea_standaloneMissingGroupName_noError() {
        // isGroup=false with the groupName key omitted entirely is also accepted.
        String json = "[{\"isGroup\":false,"
                + "\"riskAreas\":[{\"riskArea\":\"Inaccurate or untimely regulatory reporting\",\"riskClusters\":[\"OR\"]}]}]";
        assertThat(validator.validate(List.of(
                row("CFG001", "A", json, null, 2, "SG")
        ))).isEmpty();
    }

    @Test
    void riskArea_mixedGroupedAndStandalone_noError() {
        String json = "[{\"groupName\":\"IT\",\"isGroup\":true,\"riskAreas\":["
                + "{\"riskArea\":\"IT Resiliency and Continuity\",\"riskClusters\":[\"OR\",\"LCS\"]}]},"
                + "{\"groupName\":\"\",\"isGroup\":false,\"riskAreas\":["
                + "{\"riskArea\":\"Transaction Capture and Execution\",\"riskClusters\":[\"OR\"]}]}]";
        assertThat(validator.validate(List.of(
                row("CFG001", "A", json, null, 2, "SG")
        ))).isEmpty();
    }

    @Test
    void riskArea_emptyRiskAreasList_addsError() {
        String json = "[{\"groupName\":\"IT\",\"isGroup\":true,\"riskAreas\":[]}]";
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", json, null, 2, "SG")
        ));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.getField()).isEqualTo("RISK_AREA");
            assertThat(e.getMessage()).contains("at least one risk area");
        });
    }

    @Test
    void riskArea_duplicateRiskAreaNames_addsError() {
        String json = "[{\"groupName\":\"IT\",\"isGroup\":true,\"riskAreas\":["
                + "{\"riskArea\":\"Cyber Risk\",\"riskClusters\":[\"OR\"]}]},"
                + "{\"groupName\":\"Data\",\"isGroup\":true,\"riskAreas\":["
                + "{\"riskArea\":\"Cyber Risk\",\"riskClusters\":[\"LCS\"]}]}]";
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", json, null, 2, "SG")
        ));
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.getField()).isEqualTo("RISK_AREA");
            assertThat(e.getMessage()).contains("Duplicate risk area");
        });
    }

    @Test
    void riskArea_validMultipleEntries_noError() {
        assertThat(validator.validate(List.of(
                row("CFG001", "A", ra("Cyber Risk", "Conduct Risk"), null, 2, "SG")
        ))).isEmpty();
    }

    // ── BIZ_UNITS reference rules ───────────────────────────────────────────────

    @Test
    void bizUnits_unknownValue_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", ra("Cyber Risk"), "Tech,UNKNOWN_BU", 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("BIZ_UNITS");
    }

    @Test
    void bizUnits_duplicateValue_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", ra("Cyber Risk"), "Tech,Tech", 2, "SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("BIZ_UNITS");
    }

    // ── LOCATIONS reference rules ───────────────────────────────────────────────

    @Test
    void locations_unknownValue_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", ra("Cyber Risk"), null, 2, "SG,UNKNOWN")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("LOCATIONS");
    }

    @Test
    void locations_duplicateValue_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", ra("Cyber Risk"), null, 2, "SG,SG")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("LOCATIONS");
    }

    @Test
    void locations_allValid_noError() {
        assertThat(validator.validate(List.of(
                row("CFG001", "A", ra("Cyber Risk"), null, 2, "SG,HK,IN")
        ))).isEmpty();
    }

    // ── Multiple errors ─────────────────────────────────────────────────────────

    @Test
    void multipleErrors_allReported() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("CFG001", "A", "NOT_JSON",       "Bad BU", 2, "BAD_LOC"),
                row("CFG001", "B", ra("Cyber Risk"), "Tech",   2, "SG")
        ));
        assertThat(errors.size()).isGreaterThanOrEqualTo(3);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Builds a single-group RISK_AREA document with the given risk area names (cluster ["OR"]). */
    private static String ra(String... riskAreas) {
        StringBuilder sb = new StringBuilder("[{\"groupName\":\"G\",\"isGroup\":true,\"riskAreas\":[");
        for (int i = 0; i < riskAreas.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"riskArea\":\"").append(riskAreas[i]).append("\",\"riskClusters\":[\"OR\"]}");
        }
        return sb.append("]}]").toString();
    }

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
