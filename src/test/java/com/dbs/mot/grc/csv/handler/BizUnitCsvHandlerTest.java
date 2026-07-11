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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class BizUnitCsvHandlerTest {

    private static final String UPLOAD   = "/api/csv/biz-units/upload";
    private static final String DOWNLOAD = "/api/csv/biz-units/download";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clear() { jdbc.execute("DELETE FROM orl_biz_unit"); }

    @Test void upload_validCsv_returns201() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.success", is(true)));
    }

    @Test void upload_persistsAllRows() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orl_biz_unit", Integer.class);
        assert count != null && count == 6;
    }

    @Test void upload_sampleCsv_persistsAllRowsIncludingCommaInFullPath() throws Exception {
        mvc.perform(multipart(UPLOAD).file(sampleCsv()).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.success", is(true)));
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orl_biz_unit", Integer.class);
        assert count != null && count == 27 : "expected 27 rows, got " + count;
    }

    @Test void upload_missingHeader_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid())).andExpect(status().isUnauthorized());
    }

    @Test void upload_blankHeader_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "  ")).andExpect(status().isUnauthorized());
    }

    @Test void upload_emptyFile_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD)
                .file(new MockMultipartFile("file", "e.csv", "text/csv", new byte[0]))
                .header("X-EGRC-UserId", "u")).andExpect(status().isBadRequest());
    }

    @Test void upload_duplicateColumnHeader_returns400() throws Exception {
        String csv = "BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,BU_NUM\n1,ALL,1,,,,\n";
        mvc.perform(multipart(UPLOAD).file(csv(csv)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("duplicate")));
    }

    @Test void upload_wrongHeaders_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD).file(csv("WRONG,HEADERS\n1,2\n")).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("headers")));
    }

    @Test void upload_nonIntegerBuNum_returns400() throws Exception {
        String csv = "BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,BU_FULL_PATH\nABC,ALL,1,,,,\n";
        mvc.perform(multipart(UPLOAD).file(csv(csv)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("BU_NUM")));
    }

    @Test void upload_duplicateBuNum_returns400() throws Exception {
        String csv = "BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,BU_FULL_PATH\n1,ALL,1,,,,\n1,Tech,2,Tech,,,\n";
        mvc.perform(multipart(UPLOAD).file(csv(csv)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("BU_NUM")));
    }

    @Test void upload_commaInBuFullPath_isNotRejected() throws Exception {
        String csv = "BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,BU_FULL_PATH\n"
                + "1,Tech & Ops,1,,,,\"ALL\\Tech, Ops\"\n";
        mvc.perform(multipart(UPLOAD).file(csv(csv)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated());
    }

    @Test void upload_hierarchyMismatch_isNotRejected() throws Exception {
        // ORL_BU_NM_L2 referencing a BU_NM that does not exist in this batch used to be rejected;
        // the reference check was removed since hierarchy no longer needs to resolve in-batch.
        String csv = "BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,BU_FULL_PATH\n"
                + "2,DTI,3,DoesNotExist,,,\n";
        mvc.perform(multipart(UPLOAD).file(csv(csv)).header("X-EGRC-UserId", "u"))
           .andExpect(status().isCreated());
    }

    @Test void upload_unknownTable_returns400() throws Exception {
        mvc.perform(multipart("/api/csv/unknown/upload").file(valid()).header("X-EGRC-UserId", "u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("unknown")));
    }

    @Test void download_returnsHeader() throws Exception {
        mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", containsString("orl_biz_unit.csv")))
           .andExpect(content().string(containsString("BU_NUM")));
    }

    @Test void download_afterUpload_containsData() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        assert body.contains("ALL") && body.contains("u");
    }

    @Test void upload_upsert_updatesExistingRow() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u")).andExpect(status().isCreated());
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId", "u2")).andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orl_biz_unit", Integer.class);
        assert count != null && count == 6;
    }

    private MockMultipartFile valid() {
        String c = "BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,ORL_BU_NM_L3,ORL_BU_NM_L4,BU_FULL_PATH\n"
                 + "1,ALL,1,,,,\n2,Tech,2,Tech,,,\n3,Ops,2,Ops,,,\n"
                 + "4,DTI,3,Tech,DTI,,\n5,BCM,4,Tech,DTI,BCM,\n7,DTC,3,Ops,DTC,,\n";
        return csv(c);
    }

    private MockMultipartFile csv(String c) {
        return new MockMultipartFile("file", "data.csv", "text/csv", c.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile sampleCsv() throws IOException {
        try (InputStream in = new ClassPathResource("biz-units.csv").getInputStream()) {
            return new MockMultipartFile("file", "biz-units.csv", "text/csv", in);
        }
    }
}
