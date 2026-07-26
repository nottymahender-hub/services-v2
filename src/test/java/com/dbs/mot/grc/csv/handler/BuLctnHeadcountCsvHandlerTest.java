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
 * Integration tests for the orl_bu_loctn_headcount CSV upload/download endpoints.
 *
 * <p>Happy-path upload uses the real {@code bu-loctn-headcount.csv} fixture from test
 * resources (34 rows). This table has no {@code category} column, so neither the CSV header,
 * the DTO nor the entity carries one.
 *
 * <p>Upsert is verified with an inline CSV row where all 4 key columns are non-null,
 * because MySQL/H2 treat each NULL as distinct in a unique index, so rows with NULL
 * key columns do not trigger ON DUPLICATE KEY UPDATE.
 */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class BuLctnHeadcountCsvHandlerTest {

    private static final String UPLOAD   = "/api/orl-configurations/bu-loctn-headcount/upload";
    private static final String DOWNLOAD = "/api/orl-configurations/bu-loctn-headcount/download";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clear() { jdbc.execute("DELETE FROM orl_bu_loctn_headcount"); }

    // ── Happy-path upload ────────────────────────────────────────────────────

    @Test void upload_validCsv_returns201() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.success", is(true)));
    }

    @Test void upload_validCsv_persistsAllRows() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u"));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orl_bu_loctn_headcount", Integer.class);
        assert count != null && count == 34;
    }

    @Test void upload_validCsv_setsCreatedBy() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "tester")).andExpect(status().isCreated());
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_bu_loctn_headcount WHERE CREATED_BY='tester'", Integer.class);
        assert cnt != null && cnt == 34;
    }

    @Test void upload_validCsv_persistsCorrectHeadcounts() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer hc = jdbc.queryForObject(
                "SELECT headcount FROM orl_bu_loctn_headcount "
                + "WHERE ORL_BU_NM_L2='Group Audit' AND ORL_BU_NM_L3 IS NULL AND location='IC Roll Up'",
                Integer.class);
        assert hc != null && hc == 46;
    }

    // ── Upsert ────────────────────────────────────────────────────────────────

    @Test void upload_upsert_updatesHeadcount() throws Exception {
        // Use a row where all 4 key columns are non-null so ON DUPLICATE KEY UPDATE fires
        // (MySQL/H2 treat NULLs as distinct in unique indexes, so rows with NULL
        // key columns do not trigger ON DUPLICATE KEY UPDATE)
        String row = csvHeader() + "Ops,DTI,SUB,SG,50\n";
        mvc.perform(multipart(UPLOAD).file(csv(row)).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        // Re-upload same key with different headcount — should upsert
        String updated = csvHeader() + "Ops,DTI,SUB,SG,999\n";
        mvc.perform(multipart(UPLOAD).file(csv(updated)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orl_bu_loctn_headcount", Integer.class);
        assert count != null && count == 1;
        Integer hc = jdbc.queryForObject(
                "SELECT headcount FROM orl_bu_loctn_headcount "
                + "WHERE ORL_BU_NM_L2='Ops' AND ORL_BU_NM_L3='DTI' AND ORL_BU_NM_L4='SUB' AND location='SG'",
                Integer.class);
        assert hc != null && hc == 999;
    }

    // ── Authentication ───────────────────────────────────────────────────────

    @Test void upload_missingUsername_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile())).andExpect(status().isUnauthorized());
    }

    @Test void upload_blankUsername_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "  "))
           .andExpect(status().isUnauthorized());
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

    @Test void upload_duplicateColumnHeader_returns400() throws Exception {
        String c = "ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,location,ORL_BU_NM_L2\nTech,,,SG,100\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.message", containsString("duplicate")));
    }

    // ── Row-level validation errors ──────────────────────────────────────────

    @Test void upload_missingL2_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD).file(csv(csvHeader() + ",,,SG,10\n")).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].field", is("ORL_BU_NM_L2")));
    }

    @Test void upload_missingLocation_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD).file(csv(csvHeader() + "Tech,,,,10\n")).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest());
    }

    @Test void upload_negativeHeadcount_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD).file(csv(csvHeader() + "Tech,,,SG,-1\n")).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest());
    }

    // ── Cross-row validation ─────────────────────────────────────────────────

    @Test void upload_duplicateKeyInBatch_returns400() throws Exception {
        String c = csvHeader() + "Tech,,,SG,10\n" + "Tech,,,SG,20\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest());
    }

    // ── Download ─────────────────────────────────────────────────────────────

    @Test void download_returnsOkWithCsvContentDisposition() throws Exception {
        mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
           .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                   .header().string("Content-Disposition", containsString("orl_bu_loctn_headcount.csv")));
    }

    @Test void download_containsExpectedHeaders() throws Exception {
        mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
           .andExpect(content().string(containsString("ORL_BU_NM_L2")));
    }

    @Test void download_afterUpload_containsUploadedData() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validFile()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        assert body.contains("GFM") && body.contains("CN") && body.contains("20");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Loads the real bu-loctn-headcount.csv fixture from test resources. */
    private MockMultipartFile validFile() throws Exception {
        try (InputStream is = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("bu-loctn-headcount.csv"),
                "bu-loctn-headcount.csv not found in test resources")) {
            return new MockMultipartFile("file", "bu-loctn-headcount.csv", "text/csv", is.readAllBytes());
        }
    }

    private String csvHeader() { return "ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,location,headcount\n"; }

    private MockMultipartFile csv(String c) {
        return new MockMultipartFile("file", "data.csv", "text/csv", c.getBytes(StandardCharsets.UTF_8));
    }
}
