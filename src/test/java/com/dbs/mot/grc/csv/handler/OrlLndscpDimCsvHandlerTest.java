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
 * <p>RISK_AREA is a grouped JSON array (see {@link com.dbs.mot.grc.util.RiskAreaParser}),
 * normalised to compact JSON on store. BU hierarchy max level = 4, so valid BIZ_UNIT_LVL
 * values are 2 and 3. EFFECT_END_DT is a required CSV column.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrlLndscpDimCsvHandlerTest {

    private static final String UPLOAD   = "/api/orl-configurations/lndscp-dim/upload";
    private static final String DOWNLOAD = "/api/orl-configurations/lndscp-dim/download";

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

        // BIZ_UNITS validate against distinct ORL_BU_NM_L{level}, so seed those columns:
        //   L2 → {Tech, Ops, CBG}, L3 → {DTI, CBG Products, Channels}, L4 → {BCM}.
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,CREATED_BY) VALUES(1,'ALL',1,NULL,NULL,NULL,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,CREATED_BY) VALUES(2,'Tech',2,'Tech',NULL,NULL,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,CREATED_BY) VALUES(3,'Ops',2,'Ops',NULL,NULL,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,CREATED_BY) VALUES(4,'DTI',3,'Tech','DTI',NULL,'seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,CREATED_BY) VALUES(5,'BCM',4,'Tech','DTI','BCM','seed')");

        // Entity 1 also carries an orl_location_ic value ('Singapore'), a second accepted column.
        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,orl_location_ic,CREATED_BY) VALUES(1,'DBS Singapore','SG','Singapore','seed')");
        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(2,'DBS Hong Kong','HK','seed')");
        jdbc.execute("INSERT INTO orl_entity_mstr(ENTITY_NUM,ENTITY_NM,orl_location,CREATED_BY) VALUES(3,'DBS India','IN','seed')");

        // Fixtures matching the real sample lndscp-dim.csv (BIZ_UNITS='CBG Products,Channels'
        // at LVL_OF_HIER=3; LOCATIONS='SG,CN').
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,CREATED_BY) VALUES(103,'CBG Products',3,'CBG','CBG Products','seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,CREATED_BY) VALUES(104,'Channels',3,'CBG','Channels','seed')");
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

    // ── Version resolution via the repository IN(:configIds) query ─────────────

    @Test void upload_reupload_bumpsVersionUsingMaxAcrossConfigIds() throws Exception {
        // Two uploads of the same CONFIG_IDs: the second resolves VERSION = MAX + 1 = 2.
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());

        Integer maxVersion = jdbc.queryForObject("SELECT MAX(VERSION) FROM orl_lndscp_dim", Integer.class);
        assert maxVersion != null && maxVersion == 2 : "expected VERSION bumped to 2, got " + maxVersion;
        Integer v2Rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE VERSION = 2", Integer.class);
        assert v2Rows != null && v2Rows == 3 : "expected 3 rows at VERSION 2, got " + v2Rows;
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

    @Test void upload_riskAreaStoredAsCompactJson() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "user1"));
        // Stored normalised (compact) JSON still contains the risk area name and has no spaces.
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE RISK_AREA LIKE '%Cyber Risk%' "
                        + "AND RISK_AREA LIKE '%\"riskClusters\":%'", Integer.class);
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
        String csv = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",, 2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(csv)).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE BIZ_UNITS IS NULL", Integer.class);
        assert cnt != null && cnt == 1;
    }

    @Test void upload_standaloneRiskAreaBlankGroupName_returns201() throws Exception {
        // A standalone risk area (isGroup=false) may leave groupName blank.
        String riskArea = "[{\"groupName\":\"IT\",\"isGroup\":true,\"riskAreas\":["
                + "{\"riskArea\":\"IT Resiliency and Continuity\",\"riskClusters\":[\"OR\",\"LCS\"]}]},"
                + "{\"groupName\":\"\",\"isGroup\":false,\"riskAreas\":["
                + "{\"riskArea\":\"Transaction Capture and Execution\",\"riskClusters\":[\"OR\"]}]}]";
        String c = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(riskArea) + ",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_dim WHERE RISK_AREA LIKE '%Transaction Capture and Execution%'",
                Integer.class);
        assert cnt != null && cnt == 1;
    }

    @Test void upload_validLevel3BizUnits_returns201() throws Exception {
        String c = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk"))   + ",DTI,3,SG\n"
                + "CFG002,Landscape B,2024-01-01,2099-12-31," + cell(ra("Conduct Risk")) + ",DTI,3,HK\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "user1"))
           .andExpect(status().isCreated());
    }

    @Test void upload_locationFromOrlLocationIc_returns201() throws Exception {
        // 'Singapore' exists only in orl_entity_mstr.orl_location_ic — it must be accepted too.
        String c = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,2,\"Singapore,HK\"\n";
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
                + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("duplicate")));
    }

    // ── Mapping errors ──────────────────────────────────────────────────────

    @Test void upload_invalidDate_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,not-a-date,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("EFFECT_START_DT")));
    }

    @Test void upload_blankEffectEndDt_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,," + cell(ra("Cyber Risk")) + ",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("EFFECT_END_DT")));
    }

    @Test void upload_effectEndDtNotAfterStartDt_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2024-01-01," + cell(ra("Cyber Risk")) + ",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("EFFECT_END_DT")))
           .andExpect(jsonPath("$.errors[0].message", containsString("must be after")));
    }

    @Test void upload_missingConfigId_returns400() throws Exception {
        String c = csvHeader() + ",Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("CONFIG_ID")));
    }

    @Test void upload_missingBizUnitLvl_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNIT_LVL")));
    }

    @Test void upload_nonIntegerBizUnitLvl_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,ABC,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNIT_LVL")));
    }

    @Test void upload_bizUnitLvlEqualTo1_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,1,SG\n";
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
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,2,\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("LOCATIONS")));
    }

    // ── Cross-row / reference errors ───────────────────────────────────────

    @Test void upload_duplicateConfigId_returns400() throws Exception {
        String c = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk"))   + ",Tech,2,SG\n"
                + "CFG001,Landscape B,2024-02-01,2099-12-31," + cell(ra("Conduct Risk")) + ",Ops,2,HK\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("CONFIG_ID")));
    }

    @Test void upload_commaInConfigId_returns400() throws Exception {
        String c = csvHeader() + "\"CFG001,CFG002\",Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("CONFIG_ID")));
    }

    @Test void upload_commaInLndscpNm_returns400() throws Exception {
        String c = csvHeader() + "CFG001,\"Land A,Land B\",2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("LNDSCP_NM")));
    }

    @Test void upload_bizUnitLvlEqualToMax_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,4,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNIT_LVL")));
    }

    @Test void upload_inconsistentBizUnitLvl_returns400() throws Exception {
        String c = csvHeader()
                + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk"))   + ",Tech,2,SG\n"
                + "CFG002,Landscape B,2024-01-01,2099-12-31," + cell(ra("Conduct Risk")) + ",DTI,3,HK\n";
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

    @Test void upload_riskAreaEmptyClusters_returns400() throws Exception {
        String emptyClusters = "[{\"groupName\":\"G\",\"isGroup\":true,"
                + "\"riskAreas\":[{\"riskArea\":\"Cyber Risk\",\"riskClusters\":[]}]}]";
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(emptyClusters) + ",Tech,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("RISK_AREA")));
    }

    @Test void upload_unknownBizUnit_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",UNKNOWN_BU,2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNITS")))
           .andExpect(jsonPath("$.errors[0].message", containsString("UNKNOWN_BU")));
    }

    @Test void upload_duplicateBizUnit_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",\"Tech,Tech\",2,SG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("BIZ_UNITS")))
           .andExpect(jsonPath("$.errors[0].message", containsString("Duplicate")));
    }

    @Test void upload_unknownLocation_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,2,UNKNOWN_LOC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("LOCATIONS")))
           .andExpect(jsonPath("$.errors[0].message", containsString("UNKNOWN_LOC")));
    }

    @Test void upload_duplicateLocation_returns400() throws Exception {
        String c = csvHeader() + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk")) + ",Tech,2,\"SG,SG\"\n";
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
                + "CFG001,Landscape A,2024-01-01,2099-12-31," + cell(ra("Cyber Risk", "Conduct Risk")) + ",\"Tech,Ops\",2,\"SG,HK\"\n"
                + "CFG002,Landscape B,2024-01-01,2099-12-31," + cell(ra("Operational Risk"))            + ",Tech,2,IN\n"
                + "CFG003,Landscape C,2024-06-01,2099-12-31," + cell(ra("Cyber Risk"))                  + ",Tech,2,SG\n";
        return csv(c);
    }

    private String csvHeader() {
        return "CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,EFFECT_END_DT,RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS\n";
    }

    /** Builds a single-group RISK_AREA document with the given risk area names (cluster ["OR"]). */
    private static String ra(String... riskAreas) {
        StringBuilder sb = new StringBuilder("[{\"groupName\":\"G\",\"isGroup\":true,\"riskAreas\":[");
        for (int i = 0; i < riskAreas.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"riskArea\":\"").append(riskAreas[i]).append("\",\"riskClusters\":[\"OR\"]}");
        }
        return sb.append("]}]").toString();
    }

    /** CSV-escapes a field value: wrap in double quotes and double any internal quotes. */
    private static String cell(String raw) {
        return "\"" + raw.replace("\"", "\"\"") + "\"";
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
