package com.dbs.mot.grc.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the single CSV download endpoint
 * {@code GET /api/orl-configurations/{configName}/download}. Per-table happy paths are covered by
 * the individual {@code *CsvHandlerTest}s (same URLs, now served by this one handler); here we
 * assert the shared routing and the unknown-config error.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrlConfigurationDownloadControllerTest {

    private static final String URL = "/api/orl-configurations/{configName}/download";

    @Autowired MockMvc mvc;

    @Test
    void download_knownConfig_returnsCsvAttachment() throws Exception {
        mvc.perform(get(URL, "biz-units"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", containsString("orl_biz_unit.csv")))
           .andExpect(header().string("Content-Type", containsString("text/csv")));
    }

    @Test
    void download_anotherKnownConfig_routesToTheRightTable() throws Exception {
        // Proves the single handler dispatches by configName, not a hard-coded table.
        mvc.perform(get(URL, "net-risk-band"))
           .andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", containsString("net_risk_band.csv")));
    }

    @Test
    void download_unknownConfig_returns404() throws Exception {
        mvc.perform(get(URL, "does-not-exist"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.success", is(false)))
           .andExpect(jsonPath("$.message", containsString("does-not-exist")));
    }
}
