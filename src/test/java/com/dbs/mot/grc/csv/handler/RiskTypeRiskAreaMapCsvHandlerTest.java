package com.dbs.mot.grc.csv.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the orl_risk_type_risk_area_map CSV upload/download endpoints.
 *
 * <p>Happy-path upload uses the real {@code risk-type-risk-area-maps.csv} fixture from
 * test resources (18 rows). This fixture includes a RISK_AREA value with an embedded
 * comma inside a quoted field ({@code "AML, CFT and Sanctions"}) and a RISK_AREA value
 * longer than 50 characters, which is why {@code RiskTypeRiskAreaMapCsvRow.riskArea}'s
 * max length was raised from 50 to 120 (matching the DB column) — this test's happy
 * path exercises both.
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RiskTypeRiskAreaMapCsvHandlerTest {

    private static final String UPLOAD   = "/api/csv/risk-type-risk-area-maps/upload";
    private static final String DOWNLOAD = "/api/csv/risk-type-risk-area-maps/download";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clear() { jdbc.execute("DELETE FROM orl_risk_type_risk_area_map"); }

    // ── Happy-path upload ────────────────────────────────────────────────────

    @Test void upload_validCsv_returns201() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.success", is(true)));
    }

    @Test void upload_validCsv_persistsAllRows() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u"));
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_risk_type_risk_area_map", Integer.class);
        assert count != null && count == 18;
    }

    @Test void upload_validCsv_setsCreatedBy() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "analyst")).andExpect(status().isCreated());
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_risk_type_risk_area_map WHERE CREATED_BY='analyst'", Integer.class);
        assert cnt != null && cnt == 18;
    }

    @Test void upload_validCsv_persistsRiskAreaCodes() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_risk_type_risk_area_map WHERE RISK_AREA='Market Abuse' AND RISK_TYPE_L4_NUM=89",
                Integer.class);
        assert cnt != null && cnt == 1;
    }

    @Test void upload_validCsv_persistsRiskAreaWithEmbeddedComma() throws Exception {
        // "AML, CFT and Sanctions" — a quoted CSV field containing a literal comma.
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_risk_type_risk_area_map "
                + "WHERE RISK_AREA='AML, CFT and Sanctions' AND RISK_TYPE_L4_NUM=97",
                Integer.class);
        assert cnt != null && cnt == 1;
    }

    @Test void upload_validCsv_persistsLongRiskArea() throws Exception {
        // RISK_AREA here is 82 characters — exceeds the old 50-char limit but fits the new 120 one.
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_risk_type_risk_area_map "
                + "WHERE RISK_AREA='System failure due to cyber events resulting in an impact to system availability' "
                + "AND RISK_TYPE_L4_NUM=66",
                Integer.class);
        assert cnt != null && cnt == 1;
    }

    @Test void upload_validCsv_persistsRiskTypeNames() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_risk_type_risk_area_map WHERE RISK_TYPE_L4_NM='IT Resilience' AND RISK_TYPE_L4_NUM=192",
                Integer.class);
        assert cnt != null && cnt == 1;
    }

    // ── Authentication ───────────────────────────────────────────────────────

    @Test void upload_missingUsername_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile())).andExpect(status().isUnauthorized());
    }

    // ── File-level errors ────────────────────────────────────────────────────

    @Test void upload_emptyFile_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD)
                .file(new MockMultipartFile("file", "e.csv", "text/csv", new byte[0]))
                .header("X-EGRC-UserId", "u")).andExpect(status().isBadRequest());
    }

    @Test void upload_wrongHeaders_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD).file(csv("WRONG,HEADERS\n1,2\n")).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("headers")));
    }

    // ── Row-level validation errors ──────────────────────────────────────────

    @Test void upload_missingRiskArea_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD).file(csv(csvHeader() + ",101,Operational Risk - Credit\n"))
                .header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("RISK_AREA")));
    }

    @Test void upload_nonIntRiskTypeL4Num_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD).file(csv(csvHeader() + "OR,ABC,Some Risk Type\n"))
                .header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("RISK_TYPE_L4_NUM")));
    }

    @Test void upload_missingRiskTypeNm_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD).file(csv(csvHeader() + "OR,101,\n")).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest());
    }

    @Test void upload_riskAreaOver120Chars_returns400() throws Exception {
        String tooLong = "X".repeat(121);
        mvc.perform(multipart(UPLOAD).file(csv(csvHeader() + tooLong + ",101,Some Risk Type\n"))
                .header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("riskArea")));
    }

    // ── Cross-row validation ─────────────────────────────────────────────────

    @Test void upload_duplicateRiskAreaAndNum_returns400() throws Exception {
        String c = csvHeader() + "OR,101,Type A\n" + "OR,101,Type B\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest());
    }

    // ── Download ─────────────────────────────────────────────────────────────

    @Test void download_returnsOkWithCsvContentDisposition() throws Exception {
        mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                   .header().string("Content-Disposition", containsString("orl_risk_type_risk_area_map.csv")));
    }

    @Test void download_containsExpectedHeaders() throws Exception {
        mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
           .andExpect(content().string(containsString("RISK_AREA")));
    }

    @Test void download_afterUpload_containsUploadedData() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u"));
        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        assert body.contains("Market Abuse") && body.contains("u");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Loads the real risk-type-risk-area-maps.csv fixture from test resources. */
    private MockMultipartFile validFile() throws Exception {
        try (InputStream is = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("risk-type-risk-area-maps.csv"),
                "risk-type-risk-area-maps.csv not found in test resources")) {
            return new MockMultipartFile("file", "risk-type-risk-area-maps.csv", "text/csv", is.readAllBytes());
        }
    }

    private String csvHeader() { return "RISK_AREA,RISK_TYPE_L4_NUM,RISK_TYPE_L4_NM\n"; }

    private MockMultipartFile csv(String c) {
        return new MockMultipartFile("file", "data.csv", "text/csv", c.getBytes(StandardCharsets.UTF_8));
    }
}
