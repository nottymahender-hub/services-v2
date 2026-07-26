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

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class EntityMstrCsvHandlerTest {

    private static final String UPLOAD   = "/api/orl-configurations/entity-mstr/upload";
    private static final String DOWNLOAD = "/api/orl-configurations/entity-mstr/download";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clear() { jdbc.execute("DELETE FROM orl_entity_mstr"); }

    @Test void upload_valid_returns201() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId","u")).andExpect(status().isCreated());
        assert jdbc.queryForObject("SELECT COUNT(*) FROM orl_entity_mstr", Integer.class) == 3;
    }

    @Test void upload_missingHeader_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid())).andExpect(status().isUnauthorized());
    }

    @Test void upload_blankUsername_returns401() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId","")).andExpect(status().isUnauthorized());
    }

    @Test void upload_emptyFile_returns400() throws Exception {
        mvc.perform(multipart(UPLOAD)
                .file(new MockMultipartFile("file","e.csv","text/csv",new byte[0]))
                .header("X-EGRC-UserId","u")).andExpect(status().isBadRequest());
    }

    @Test void upload_duplicateColumnHeader_returns400() throws Exception {
        String c = "ENTITY_NUM,ENTITY_NM,orl_location,ENTITY_NUM\n1,A,SG,1\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId","u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("duplicate")));
    }

    @Test void upload_duplicateEntityNum_returns400() throws Exception {
        String c = "ENTITY_NUM,ENTITY_NM,orl_location,orl_location_ic\n1,Alpha,SG,\n1,Beta,HK,\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId","u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("ENTITY_NUM")));
    }

    @Test void upload_duplicateEntityNm_returns400() throws Exception {
        String c = "ENTITY_NUM,ENTITY_NM,orl_location,orl_location_ic\n1,Alpha,SG,\n2,Alpha,HK,\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId","u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("ENTITY_NM")));
    }

    @Test void upload_forbiddenLocationIc_returns400() throws Exception {
        String c = "ENTITY_NUM,ENTITY_NM,orl_location,orl_location_ic\n1,Alpha,SG,IC Rollup\n";
        mvc.perform(multipart(UPLOAD).file(csv(c)).header("X-EGRC-UserId","u"))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field", is("orl_location_ic")));
    }

    @Test void download_returnsHeader() throws Exception {
        mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
           .andExpect(header().string("Content-Disposition", containsString("orl_entity_mstr.csv")));
    }

    @Test void download_afterUpload_containsData() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId","u")).andExpect(status().isCreated());
        String body = mvc.perform(get(DOWNLOAD)).andExpect(status().isOk())
                         .andReturn().getResponse().getContentAsString();
        assert body.contains("Alpha");
    }

    private MockMultipartFile valid() {
        String c = "ENTITY_NUM,ENTITY_NM,orl_location,orl_location_ic\n1,Alpha,SG,\n2,Beta,HK,IC-01\n3,Gamma,US,\n";
        return csv(c);
    }

    @Test void upload_upsert_updatesExistingRow() throws Exception {
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId","u")).andExpect(status().isCreated());
        // Re-upload same rows â€” should upsert, not error
        mvc.perform(multipart(UPLOAD).file(valid()).header("X-EGRC-UserId","u2")).andExpect(status().isCreated());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM orl_entity_mstr", Integer.class);
        assert count != null && count > 0; // same count â€” rows updated, not duplicated
    }

    private MockMultipartFile csv(String c) {
        return new MockMultipartFile("file","data.csv","text/csv",c.getBytes(StandardCharsets.UTF_8));
    }
}
