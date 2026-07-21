package com.dbs.mot.grc.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link LandscapeAssmtController}.
 *
 * <p>Uses H2 in-memory DB (schema-test.sql). Reference data is seeded in
 * {@link #setUp()} before every test so each test is fully independent.
 *
 * <p>Seed data summary:
 * <ul>
 *   <li>Two landscape configs: id=1 "Landscape Alpha", id=2 "Landscape Beta"</li>
 *   <li>Three assessments: Alpha/Q1-2024 (with update), Alpha/Q2-2024 (no update), Beta/Q1-2024</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LandscapeAssmtControllerTest {

    private static final String URL = "/landscapes";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // Clear in FK-safe order (child tables first)
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");

        // Seed landscape configs
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim
                    (id, CONFIG_ID, LNDSCP_NM, EFFECT_START_DT, VERSION, RISK_AREA, LOCATIONS, CREATED_BY)
                VALUES (1, 'CFG001', 'Landscape Alpha', DATE '2024-01-01', 1, '[{"groupName":"IT","isGroup":false,"riskAreas":[{"riskArea":"Cyber Risk","riskClusters":["OR"]}]}]', 'SG', 'seed')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim
                    (id, CONFIG_ID, LNDSCP_NM, EFFECT_START_DT, VERSION, RISK_AREA, LOCATIONS, CREATED_BY)
                VALUES (2, 'CFG002', 'Landscape Beta', DATE '2024-01-01', 1, '[{"groupName":"Conduct","isGroup":false,"riskAreas":[{"riskArea":"Conduct Risk","riskClusters":["CR"]}]}]', 'HK', 'seed')
                """);

        // Seed assessments:
        //   id=1 : Alpha / Q1-2024 — has UPDATED_BY and UPDATE_DT_TM set
        //   id=2 : Alpha / Q2-2024 — UPDATED_BY and UPDATE_DT_TM are NULL (fall back to CREATE)
        //   id=3 : Beta  / Q1-2024 — UPDATED_BY set, UPDATE_DT_TM NULL
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt
                    (id, LNDSCP_NUM, ASSEMT_PERIOD, status, CREATED_BY, CREATE_DT_TM, UPDATE_DT_TM, UPDATED_BY)
                VALUES (1, 1, 'Q1-2024', 'Open',
                        'creator1', TIMESTAMP '2024-01-10 09:00:00',
                        TIMESTAMP '2024-01-15 12:00:00', 'updater1')
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt
                    (id, LNDSCP_NUM, ASSEMT_PERIOD, status, CREATED_BY, CREATE_DT_TM, UPDATE_DT_TM, UPDATED_BY)
                VALUES (2, 1, 'Q2-2024', 'Draft',
                        'creator2', TIMESTAMP '2024-04-01 08:00:00', NULL, NULL)
                """);
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt
                    (id, LNDSCP_NUM, ASSEMT_PERIOD, status, CREATED_BY, CREATE_DT_TM, UPDATE_DT_TM, UPDATED_BY)
                VALUES (3, 2, 'Q1-2024', 'Locked',
                        'creator3', TIMESTAMP '2024-01-20 10:00:00', NULL, NULL)
                """);
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Test
    void missingXUsername_returns401() throws Exception {
        mvc.perform(get(URL))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.success", is(false)))
           .andExpect(jsonPath("$.message", containsString("X-EGRC-UserId")));
    }

    @Test
    void blankXUsername_returns401() throws Exception {
        mvc.perform(get(URL).header("X-EGRC-UserId", "   "))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void emptyXUsername_returns401() throws Exception {
        mvc.perform(get(URL).header("X-EGRC-UserId", ""))
           .andExpect(status().isUnauthorized());
    }

    // ── Happy path — data present ─────────────────────────────────────────────

    @Test
    void getAll_withData_returns200() throws Exception {
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void getAll_returnsCorrectCount() throws Exception {
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void getAll_firstRecord_hasCorrectLandscapeName() throws Exception {
        // Most recently modified first: Alpha/Q2 (2024-04-01)
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[0].landscapeName", is("Landscape Alpha")));
    }

    @Test
    void getAll_firstRecord_hasCorrectAssessmentPeriod() throws Exception {
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[0].assessmentPeriod", is("Q2-2024")));
    }

    @Test
    void getAll_firstRecord_hasCorrectStatus() throws Exception {
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[0].status", is("Draft")));
    }

    @Test
    void getAll_recordWithUpdate_usesUpdateDtTmAndUpdatedBy() throws Exception {
        // Assessment id=1 (Alpha/Q1) has UPDATE_DT_TM + UPDATED_BY; it is the oldest → last row.
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[2].lastModifiedBy", is("updater1")))
           .andExpect(jsonPath("$.data[2].lastModifiedOn", is("2024-01-15 12:00:00")));
    }

    @Test
    void getAll_recordWithoutUpdate_fallsBackToCreateDtTmAndCreatedBy() throws Exception {
        // Assessment id=2 (Alpha/Q2) has no update → falls back to CREATE columns; newest → first row.
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[0].lastModifiedBy", is("creator2")))
           .andExpect(jsonPath("$.data[0].lastModifiedOn", is("2024-04-01 08:00:00")));
    }

    @Test
    void getAll_orderedByLastModifiedOnDescending() throws Exception {
        // lastModifiedOn desc: Alpha/Q2 (2024-04-01), Beta/Q1 (2024-01-20), Alpha/Q1 (2024-01-15)
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[0].landscapeName", is("Landscape Alpha")))
           .andExpect(jsonPath("$.data[0].assessmentPeriod", is("Q2-2024")))
           .andExpect(jsonPath("$.data[1].landscapeName", is("Landscape Beta")))
           .andExpect(jsonPath("$.data[1].assessmentPeriod", is("Q1-2024")))
           .andExpect(jsonPath("$.data[2].landscapeName", is("Landscape Alpha")))
           .andExpect(jsonPath("$.data[2].assessmentPeriod", is("Q1-2024")));
    }

    @Test
    void getAll_responseContainsSuccessMessageWithCount() throws Exception {
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message", containsString("3")));
    }

    // ── Happy path — empty table ──────────────────────────────────────────────

    @Test
    void getAll_emptyTable_returnsEmptyArrayWith200() throws Exception {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");

        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.success", is(true)))
           .andExpect(jsonPath("$.data", hasSize(0)))
           .andExpect(jsonPath("$.message", containsString("No landscape")));
    }

    @Test
    void getAll_emptyTable_messageIndicatesNoData() throws Exception {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");

        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.message", is("No landscape assessments found.")));
    }

    // ── Response structure ────────────────────────────────────────────────────

    @Test
    void getAll_allExpectedFieldsPresent() throws Exception {
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[0].landscapeAssmtId").exists())
           .andExpect(jsonPath("$.data[0].landscapeName").exists())
           .andExpect(jsonPath("$.data[0].assessmentPeriod").exists())
           .andExpect(jsonPath("$.data[0].lastModifiedOn").exists())
           .andExpect(jsonPath("$.data[0].lastModifiedBy").exists())
           .andExpect(jsonPath("$.data[0].status").exists());
    }

    @Test
    void getAll_landscapeAssmtIdMatchesDbId() throws Exception {
        // First row is Alpha/Q2-2024 = assessment id 2 (most recently modified)
        mvc.perform(get(URL).header("X-EGRC-UserId", "tester"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data[0].landscapeAssmtId", is(2)));
    }
}
