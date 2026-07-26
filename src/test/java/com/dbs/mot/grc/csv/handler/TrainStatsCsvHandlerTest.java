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
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class TrainStatsCsvHandlerTest {

    private static final String UPLOAD   = "/api/orl-configurations/train-stats/upload";
    private static final String DOWNLOAD = "/api/orl-configurations/train-stats/download";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clear() { jdbc.execute("DELETE FROM train_stats"); }

    @Test void upload_sampleCsv_persistsAllRowsWithVersion1() throws Exception {
        mvc.perform(multipart(UPLOAD).file(sampleCsv()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM train_stats", Integer.class);
        assert count != null && count == 28 : "expected 28 rows, got " + count;
        Integer maxVersion = jdbc.queryForObject("SELECT MAX(config_version) FROM train_stats", Integer.class);
        assert maxVersion != null && maxVersion == 1;
    }

    @Test void upload_sameFileTwice_incrementsVersion() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM train_stats", Integer.class);
        assert count != null && count == 6;
        Integer v2Count = jdbc.queryForObject("SELECT COUNT(*) FROM train_stats WHERE config_version=2", Integer.class);
        assert v2Count != null && v2Count == 3;
    }

    @Test void upload_missingHeader_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid())).andExpect(status().isUnauthorized());
    }

    @Test void upload_duplicateColumnHeader_returns400() throws Exception {
        String c = "lvl,train_mean,train_var,lvl\nL2,0.1,0.2,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("duplicate")));
    }

    @Test void upload_invalidModule_returns400() throws Exception {
        String c = "lvl,train_mean,train_var,module\nL2,0.1,0.2,WRONG\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("module")));
    }

    @Test void upload_invalidLvl_returns400() throws Exception {
        String c = "lvl,train_mean,train_var,module\nWRONG,0.1,0.2,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("lvl")));
    }

    @Test void upload_invalidDecimal_returns400() throws Exception {
        String c = "lvl,train_mean,train_var,module\nL2,NOTNUM,0.2,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("train_mean")));
    }

    @Test void upload_duplicateCombination_returns400() throws Exception {
        String c = "lvl,train_mean,train_var,module\nL2,0.1,0.2,INC\nL2,0.3,0.4,INC\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.errors[0].row", is(2)));
    }

    @Test void download_returnsHeader() throws Exception {
        mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", containsString("train_stats.csv")));
    }

    @Test void download_afterUpload_containsData() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        assert body.contains("INC") && body.contains("L2") && body.contains("u");
    }

    @Test void download_afterReupload_returnsOnlyLatestVersionPerGroup() throws Exception {
        // First upload creates version 1 (3 rows, one per lvl); re-upload bumps all to version 2.
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());

        Integer totalRows = jdbc.queryForObject("SELECT COUNT(*) FROM train_stats", Integer.class);
        assert totalRows != null && totalRows == 6;

        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        List<String> dataLines = body.lines().skip(1).filter(l -> !l.isBlank()).collect(Collectors.toList());
        assert dataLines.size() == 3 : "expected 3 data rows in download, got " + dataLines.size();
        // Download header: id, config_version, lvl, ... → config_version is column index 1.
        // OpenCSV quotes every field, so strip the surrounding quotes before comparing.
        assert dataLines.stream().allMatch(l -> l.split(",")[1].replace("\"", "").equals("2"))
                : "every data row must carry config_version=2, got: " + dataLines;
    }

    private MockMultipartFile valid() {
        String c = "lvl,train_mean,train_var,module\nL2,0.12,0.65,INC\nL3,1.23,0.76,INC\nL4,2.34,0.87,INC\n";
        return csv(c);
    }

    private MockMultipartFile csv(String c) {
        return new MockMultipartFile("file", "data.csv", "text/csv", c.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile sampleCsv() throws IOException {
        try (InputStream in = new ClassPathResource("train-stats.csv").getInputStream()) {
            return new MockMultipartFile("file", "train-stats.csv", "text/csv", in);
        }
    }
}
