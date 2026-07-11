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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class NetRiskBandCsvHandlerTest {

    private static final String UPLOAD   = "/api/csv/net-risk-band/upload";
    private static final String DOWNLOAD = "/api/csv/net-risk-band/download";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clear() {
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details"); // FK child of net_risk_band
        jdbc.execute("DELETE FROM net_risk_band");
    }

    @Test void upload_sampleCsv_persistsAllRowsWithVersion1() throws Exception {
        mvc.perform(multipart(UPLOAD).file(sampleCsv()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM net_risk_band", Integer.class);
        assert count != null && count == 16 : "expected 16 rows, got " + count;
        Integer maxVersion = jdbc.queryForObject("SELECT MAX(config_version) FROM net_risk_band", Integer.class);
        assert maxVersion != null && maxVersion == 1;
    }

    @Test void upload_sampleCsv_blankRangeCellsUseSentinels() throws Exception {
        mvc.perform(multipart(UPLOAD).file(sampleCsv()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer sentinelLowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM net_risk_band WHERE range_low = ?",
                Integer.class, new BigDecimal("-9999999999.999999"));
        Integer sentinelHighCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM net_risk_band WHERE range_high = ?",
                Integer.class, new BigDecimal("9999999999.999999"));
        assert sentinelLowCount != null && sentinelLowCount == 4 : "expected 4 open-ended low rows (Low rating per module)";
        assert sentinelHighCount != null && sentinelHighCount == 4 : "expected 4 open-ended high rows (High rating per module)";
    }

    @Test void upload_sameFileTwice_incrementsVersion() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM net_risk_band", Integer.class);
        assert count != null && count == 8;
        Integer v2Count = jdbc.queryForObject("SELECT COUNT(*) FROM net_risk_band WHERE config_version=2", Integer.class);
        assert v2Count != null && v2Count == 4;
    }

    @Test void upload_missingHeader_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid())).andExpect(status().isUnauthorized());
    }

    @Test void upload_duplicateColumnHeader_returns400() throws Exception {
        String c = "range_low,range_high,net_risk_rtng,net_risk_rtng\n0,1,Low,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("duplicate")));
    }

    @Test void upload_invalidModule_returns400() throws Exception {
        String c = "range_low,range_high,net_risk_rtng,module\n0,1,Low,INVALID\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("module")));
    }

    @Test void upload_invalidNetRiskRtng_returns400() throws Exception {
        String c = "range_low,range_high,net_risk_rtng,module\n0,1,INVALID,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("net_risk_rtng")));
    }

    @Test void upload_nonNumericRangeLow_returns400() throws Exception {
        String c = "range_low,range_high,net_risk_rtng,module\nABC,1,Low,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("range_low")));
    }

    @Test void upload_duplicateCombination_returns400() throws Exception {
        String c = "range_low,range_high,net_risk_rtng,module\n0,0.5,Low,INC\n0.5,1,Low,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].row", is(2)));
    }

    @Test void download_returnsHeader() throws Exception {
        mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", containsString("net_risk_band.csv")));
    }

    @Test void download_afterUpload_containsData() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        assert body.contains("INC") && body.contains("Low") && body.contains("u");
    }

    @Test void download_afterReupload_returnsOnlyLatestVersionPerGroup() throws Exception {
        // First upload creates version 1 (4 rows, one per net_risk_rtng); re-upload bumps all to version 2.
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());

        Integer totalRows = jdbc.queryForObject("SELECT COUNT(*) FROM net_risk_band", Integer.class);
        assert totalRows != null && totalRows == 8;

        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        List<String> dataLines = body.lines().skip(1).filter(l -> !l.isBlank()).collect(Collectors.toList());
        assert dataLines.size() == 4 : "expected 4 data rows in download, got " + dataLines.size();
        // Download header: id, config_version, range_low, range_high, ... → config_version is column index 1.
        // OpenCSV quotes every field, so strip the surrounding quotes before comparing.
        assert dataLines.stream().allMatch(l -> l.split(",")[1].replace("\"", "").equals("2"))
                : "every data row must carry config_version=2, got: " + dataLines;
    }

    private MockMultipartFile valid() {
        String c = "range_low,range_high,net_risk_rtng,module\n"
                 + "0.0,0.25,Low,INC\n0.25,0.50,Med Low,INC\n"
                 + "0.50,0.75,Med High,INC\n0.75,1.00,High,INC\n";
        return csv(c);
    }

    private MockMultipartFile csv(String c) {
        return new MockMultipartFile("file", "data.csv", "text/csv", c.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile sampleCsv() throws IOException {
        try (InputStream in = new ClassPathResource("net-risk-band.csv").getInputStream()) {
            return new MockMultipartFile("file", "net-risk-band.csv", "text/csv", in);
        }
    }
}
