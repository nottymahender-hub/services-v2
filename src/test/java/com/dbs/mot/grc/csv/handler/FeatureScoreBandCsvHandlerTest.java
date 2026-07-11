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
class FeatureScoreBandCsvHandlerTest {

    private static final String UPLOAD   = "/api/csv/feature-score-band/upload";
    private static final String DOWNLOAD = "/api/csv/feature-score-band/download";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clear() { jdbc.execute("DELETE FROM feature_score_band"); }

    @Test void upload_sampleCsv_persistsAllRowsWithVersion1() throws Exception {
        mvc.perform(multipart(UPLOAD).file(sampleCsv()).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.success", is(true)));

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM feature_score_band", Integer.class);
        assert count != null && count == 34 : "expected 34 rows, got " + count;
        Integer maxVersion = jdbc.queryForObject("SELECT MAX(config_version) FROM feature_score_band", Integer.class);
        assert maxVersion != null && maxVersion == 1;
    }

    @Test void upload_sampleCsv_blankRangeCellsUseSentinels() throws Exception {
        mvc.perform(multipart(UPLOAD).file(sampleCsv()).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated());

        // At least one row in the sample has a blank range_low or range_high (open-ended bin).
        Integer sentinelLowCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM feature_score_band WHERE range_low = ?",
                Integer.class, new BigDecimal("-9999999999.999999"));
        Integer sentinelHighCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM feature_score_band WHERE range_high = ?",
                Integer.class, new BigDecimal("9999999999.999999"));
        assert sentinelLowCount != null && sentinelLowCount > 0 : "expected at least one open-ended low sentinel";
        assert sentinelHighCount != null && sentinelHighCount > 0 : "expected at least one open-ended high sentinel";
    }

    @Test void upload_sameFileTwice_incrementsVersionPerGroup() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validInc()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(validInc()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM feature_score_band", Integer.class);
        assert count != null && count == 6;
        Integer v2Count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM feature_score_band WHERE config_version=2", Integer.class);
        assert v2Count != null && v2Count == 3;
    }

    @Test void upload_missingHeader_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validInc())).andExpect(status().isUnauthorized());
    }

    @Test void upload_duplicateColumnHeader_returns400() throws Exception {
        String c = "feature_name,feature_bin,range_low,range_high,score,feature_name\n"
                + "inc_is_sinp_count_l3m_mtd,1,0,1,10,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("duplicate")));
    }

    @Test void upload_wrongHeaders_returns400() throws Exception {
        String c = "config_version,feature_bin,feature_name,range_low,range_high,score,module\n"
                + "1,1,inc_is_sinp_count_l3m_mtd,0,1,10,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest());
    }

    @Test void upload_invalidModule_returns400() throws Exception {
        String c = "feature_name,feature_bin,range_low,range_high,score,module\n"
                + "inc_is_sinp_count_l3m_mtd,1,0,1,10,INVALID\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("module")));
    }

    @Test void upload_invalidDecimal_returns400() throws Exception {
        String c = "feature_name,feature_bin,range_low,range_high,score,module\n"
                + "inc_is_sinp_count_l3m_mtd,1,NOTNUM,1,10,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("range_low")));
    }

    @Test void upload_disallowedFeatureName_returns400() throws Exception {
        String c = "feature_name,feature_bin,range_low,range_high,score,module\n"
                + "unknown_feature,1,0,1,10,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("feature_name")));
    }

    @Test void upload_duplicateCombination_returns400WithRowIdentified() throws Exception {
        String c = "feature_name,feature_bin,range_low,range_high,score,module\n"
                + "inc_is_sinp_count_l3m_mtd,1,0,1,10,INC\n"
                + "inc_is_sinp_count_l3m_mtd,1,1,2,20,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].row", is(2)))
           .andExpect(jsonPath("$.errors[0].message", containsString("row 1")));
    }

    @Test void upload_sameFeatureNameDifferentModules_returns201() throws Exception {
        String c = "feature_name,feature_bin,range_low,range_high,score,module\n"
                + "inc_is_sinp_count_l3m_mtd,1,0,1,10,INC\n"
                + "inc_is_sinp_count_l3m_mtd,1,0,1,10,TPRM\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated());
    }

    @Test void upload_validIna_returns201() throws Exception {
        mvc.perform(multipart(UPLOAD).file(validIna()).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.success", is(true)));
    }

    @Test void download_returnsHeader() throws Exception {
        mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", containsString("feature_score_band.csv")));
    }

    @Test void download_afterReupload_returnsOnlyLatestVersionPerGroup() throws Exception {
        // First upload creates version 1 (3 rows); re-upload of the same groups bumps to version 2.
        mvc.perform(multipart(UPLOAD).file(validInc()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(validInc()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());

        // 6 rows exist in the table (v1 + v2) but the download must only contain the 3 v2 rows.
        Integer totalRows = jdbc.queryForObject("SELECT COUNT(*) FROM feature_score_band", Integer.class);
        assert totalRows != null && totalRows == 6;

        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        List<String> dataLines = body.lines().skip(1).filter(l -> !l.isBlank()).collect(Collectors.toList());
        assert dataLines.size() == 3 : "expected 3 data rows in download, got " + dataLines.size();
        // Download header: id, config_version, feature_bin, feature_name, ...  → config_version is column index 1.
        // OpenCSV quotes every field, so strip the surrounding quotes before comparing.
        assert dataLines.stream().allMatch(l -> l.split(",")[1].replace("\"", "").equals("2"))
                : "every data row must carry config_version=2, got: " + dataLines;
    }

    @Test void download_multipleGroupsAtDifferentVersions_eachReturnsItsOwnLatest() throws Exception {
        // INC group re-uploaded to version 2; INA group stays at version 1 — each group's
        // "latest" is independent of the others.
        mvc.perform(multipart(UPLOAD).file(validInc()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(validInc()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(validIna()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());

        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        long dataLineCount = body.lines().skip(1).filter(l -> !l.isBlank()).count();
        assert dataLineCount == 5 : "expected 3 INC(v2) + 2 INA(v1) = 5 rows, got " + dataLineCount;
    }

    // ── CSV builders ─────────────────────────────────────────────────────────

    private MockMultipartFile validInc() {
        String c = "feature_name,feature_bin,range_low,range_high,score,module\n"
                + "inc_is_sinp_count_l3m_mtd,1,0,1,10,INC\n"
                + "inc_is_mi_count_l3m_mtd,2,1,2,20,INC\n"
                + "inc_is_gorc_count_l3m_mtd_per_50_fte,3,2,3,30,INC\n";
        return csv(c);
    }

    private MockMultipartFile validIna() {
        String c = "feature_name,feature_bin,range_low,range_high,score,module\n"
                + "issue_rating_high_count_per_50_fte,1,0,1,10,INA\n"
                + "issue_rating_medium_count_per_50_fte,2,1,2,20,INA\n";
        return csv(c);
    }

    private MockMultipartFile csv(String c) {
        return new MockMultipartFile("file", "data.csv", "text/csv", c.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile sampleCsv() throws IOException {
        try (InputStream in = new ClassPathResource("feature-score-band.csv").getInputStream()) {
            return new MockMultipartFile("file", "feature-score-band.csv", "text/csv", in);
        }
    }
}
