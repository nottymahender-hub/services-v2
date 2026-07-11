package com.dbs.mot.grc.csv.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the orl_lndscp_dim CSV upload/download endpoints.
 *
 * <p>RISK_AREA is now a JSON string, e.g. {@code {"Cyber Risk":["OR"],"Conduct Risk":["CR"]}}.
 * BU hierarchy max level = 4, so valid BIZ_UNIT_LVL values are 2 and 3.
 * EFFECT_END_DT is now a required CSV column (no longer hardcoded to 9999-12-31).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrlLndscpDimCsvHandlerTest {

    private static final String UPLOAD   = "/api/csv/lndscp-dim/upload";
    private static final String DOWNLOAD = "/api/csv/lndscp-dim/download";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        jdbc.execute("DELETE FROM orl_biz_unit");
        jdbc.execute("DELETE FROM orl_entity_mstr");

        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(1,'ALL',1,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(2,'Tech',2,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(3,'Ops',2,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(4,'DTI',3,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(5,'BCM',4,'seed')");

        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(1,'DBS Singapore','SG','seed')");
        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(2,'DBS Hong Kong','HK','seed')");
        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(3,'DBS India','IN','seed')");

        // Fixtures for the real sample lndscp-dim.csv (BIZ_UNITS=Technology,Operations LVL_OF_HIER=2; LOCATIONS=SG,CN)
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(101,'Technology',2,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,CREATED_BY) VALUES(102,'Operations',2,'seed')");
        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(101,'DBS Sample SG','SG','seed')");
        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(102,'DBS Sample CN','CN','seed')");
    }

    // ── Happy-path uploads ──────────────────────────────────────────────────

    @Test void upload_valid_returns201() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.success", is(true)));
    }

    @Test void upload_sampleCsv_persistsRowWithGivenEffectEndDt() throws Exception {
        mvc.perform(multipart(UPLOAD).file(sampleCsv()).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orl_lndscp_dim", Integer.class);
        assert count != null && count == 1;
        Integer matching = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE EFFECT_START_DT = DATE '2026-07-02' "
                        + "AND EFFECT_END_DT = DATE '2029-07-02'", Integer.class);
        assert matching != null && matching == 1;
    }

    @Test void upload_persistsAllRows() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orl_lndscp_dim", Integer.class);
        assert count != null && count == 3;
    }

    @Test void upload_setsVersionToOne_firstUpload() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"));
        Integer v = jdbc.queryForObject("SELECT MIN(VERSION) FROM orl_lndscp_dim", Integer.class);
        assert v != null && v == 1;
    }

    @Test void upload_setsDefaultStatusActive() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"));
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE STATUS='ACTIVE'", Integer.class);
        assert cnt != null && cnt == 3;
    }

    @Test void upload_persistsGivenEffectEndDate() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"));
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE EFFECT_END_DT = DATE '2099-12-31'",
                Integer.class);
        assert cnt != null && cnt == 3;
    }

    @Test void upload_persistsBizUnitLvl() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"));
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE BIZ_UNIT_LVL=2", Integer.class);
        assert cnt != null && cnt == 3;
    }

    @Test void upload_riskAreaStoredAsJson() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"));
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE RISK_AREA LIKE '%Cyber Risk%'", Integer.class);
        assert cnt != null && cnt >= 1;
    }

    @Test void upload_sameFileTwice_incrementsVersion() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM orl_lndscp_dim", Integer.class);
        Integer v2    = jdbc.queryForObject("SELECT COUNT(*) FROM orl_lndscp_dim WHERE VERSION=2", Integer.class);
        assert total != null && total == 6;
        assert v2    != null && v2    == 3;
    }

    @Test void upload_emptyBizUnits_persistedAsNull() throws Exception {
        String csv = "CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,EFFECT_END_DT,RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS\n"
                + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",, 2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(csv)).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE BIZ_UNITS IS NULL", Integer.class);
        assert cnt != null && cnt == 1;
    }

    @Test void upload_validLevel3BizUnits_returns201() throws Exception {
        String c = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",DTI,3,SG\n"
                + "CFG002,Landscape B,2024-01-01,2099-12-31,\"{\"\"Conduct Risk\"\":[\"\"CR\"\"]}\",DTI,3,HK\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
    }

    // ── Auth errors ─────────────────────────────────────────────────────────

    @Test void upload_missingXUsername_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()))
           .andExpect(status().isUnauthorized());
    }

    @Test void upload_blankXUsername_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "  "))
           .andExpect(status().isUnauthorized());
    }

    // ── File-level errors ───────────────────────────────────────────────────

    @Test void upload_emptyFile_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD)
                .file(new MockMultipartFile("file", "e.csv", "text/csv", new byte[0]))
                .header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest());
    }

    @Test void upload_wrongHeaders_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD).file(csv("WRONG,HEADERS\n1,2\n")).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("headers")));
    }

    @Test void upload_duplicateColumnHeader_returns400() throws Exception {
        String c = "CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,EFFECT_END_DT,RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,CONFIG_ID\n"
                + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("duplicate")));
    }

    // ── Mapping errors ──────────────────────────────────────────────────────

    @Test void upload_invalidDate_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,not-a-date,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("EFFECT_START_DT")));
    }

    @Test void upload_blankEffectEndDt_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("EFFECT_END_DT")));
    }

    @Test void upload_effectEndDtNotAfterStartDt_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2024-01-01,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("EFFECT_END_DT")))
           .andExpect(jsonPath("$.errors[0].message", containsString("must be after")));
    }

    @Test void upload_missingConfigId_returns400() throws Exception {
        String c = csvHeader() + ",Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("CONFIG_ID")));
    }

    @Test void upload_missingBizUnitLvl_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNIT_LVL")));
    }

    @Test void upload_nonIntegerBizUnitLvl_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,ABC,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNIT_LVL")));
    }

    @Test void upload_bizUnitLvlEqualTo1_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,1,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("bizUnitLvl")));
    }

    @Test void upload_missingRiskArea_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,,Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("RISK_AREA")));
    }

    @Test void upload_missingLocations_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("LOCATIONS")));
    }

    // ── Cross-row / reference errors ───────────────────────────────────────

    @Test void upload_duplicateConfigId_returns400() throws Exception {
        String c = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n"
                + "CFG001,Landscape B,2024-02-01,2099-12-31,\"{\"\"Conduct Risk\"\":[\"\"CR\"\"]}\",Ops,2,HK\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("CONFIG_ID")));
    }

    @Test void upload_commaInConfigId_returns400() throws Exception {
        String c = csvHeader() + "\"CFG001,CFG002\",Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("CONFIG_ID")));
    }

    @Test void upload_commaInLndscpNm_returns400() throws Exception {
        String c = csvHeader() + "CFG001,\"Land A,Land B\",2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("LNDSCP_NM")));
    }

    @Test void upload_bizUnitLvlEqualToMax_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,4,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNIT_LVL")));
    }

    @Test void upload_inconsistentBizUnitLvl_returns400() throws Exception {
        String c = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n"
                + "CFG002,Landscape B,2024-01-01,2099-12-31,\"{\"\"Conduct Risk\"\":[\"\"CR\"\"]}\",DTI,3,HK\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNIT_LVL")));
    }

    @Test void upload_invalidRiskAreaJson_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,NOT_JSON,Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("RISK_AREA")));
    }

    @Test void upload_riskAreaJsonEmptyArray_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\":[]}\",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest());
    }

    @Test void upload_unknownBizUnit_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",UNKNOWN_BU,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNITS")))
           .andExpect(jsonPath("$.errors[0].message", containsString("UNKNOWN_BU")));
    }

    @Test void upload_duplicateBizUnit_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",\"Tech,Tech\",2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNITS")))
           .andExpect(jsonPath("$.errors[0].message", containsString("Duplicate")));
    }

    @Test void upload_unknownLocation_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,UNKNOWN_LOC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("LOCATIONS")))
           .andExpect(jsonPath("$.errors[0].message", containsString("UNKNOWN_LOC")));
    }

    @Test void upload_duplicateLocation_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,\"SG,SG\"\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("LOCATIONS")))
           .andExpect(jsonPath("$.errors[0].message", containsString("Duplicate")));
    }

    // ── Download ────────────────────────────────────────────────────────────

    @Test void download_returnsHeader() throws Exception {
        mvc.perform(get(DOWNLOAD))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", containsString("orl_lndscp_dim.csv")));
    }

    @Test void download_includesAllColumnHeaders() throws Exception {
        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        assert body.contains("CONFIG_ID") && body.contains("RISK_AREA")
                && body.contains("BIZ_UNIT_LVL") && body.contains("VERSION");
    }

    @Test void download_afterUpload_containsData() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        assert body.contains("CFG001") && body.contains("user1") && body.contains("Cyber Risk");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private MockMultipartFile valid() {
        String c = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"],\"\"Conduct Risk\"\":[\"\"CR\"\"]}\",\"Tech,Ops\",2,\"SG,HK\"\n"
                + "CFG002,Landscape B,2024-01-01,2099-12-31,\"{\"\"Operational Risk\"\":[\"\"OR\"\"]}\",Tech,2,IN\n"
                + "CFG003,Landscape C,2024-06-01,2099-12-31,\"{\"\"Cyber Risk\"\":[\"\"OR\"\"]}\",Tech,2,SG\n";
        return csv(c);
    }

    private String csvHeader() {
        return "CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,EFFECT_END_DT,RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS\n";
    }

    private MockMultipartFile csv(String c) {
        return new MockMultipartFile("file", "data.csv", "text/csv",
                c.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile sampleCsv() throws IOException {
        try (InputStream in = new ClassPathResource("lndscp-dim.csv").getInputStream()) {
            return new MockMultipartFile("file", "lndscp-dim.csv", "text/csv", in);
        }
    }
}
