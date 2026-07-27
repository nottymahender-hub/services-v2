package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.CalloutRequest;
import com.dbs.mot.grc.dto.CalloutResponse;
import com.dbs.mot.grc.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated tests for {@link LandscapeAssmtCalloutService} against H2 — create/read/update/
 * soft-delete, the JSON round-trip of the location/BU arrays, the SME ownership shift on update,
 * and the not-found / cross-assessment guards.
 */
@SpringBootTest
@ActiveProfiles("test")
class LandscapeAssmtCalloutServiceTest {

    private static final long ASSMT = 30L;
    private static final long OTHER_ASSMT = 31L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired LandscapeAssmtCalloutService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout_comment_hist");
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,'[]',2,'SG','seed')
                """);
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) "
                + "VALUES(" + ASSMT + ",1,'Q1-2024','Open','seed')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) "
                + "VALUES(" + OTHER_ASSMT + ",1,'Q2-2024','Open','seed')");
    }

    // ── create + read ─────────────────────────────────────────────────────────

    @Test
    void createCallout_thenGetCallouts_returnsRoundTrippedCallout() {
        service.createCallout(ASSMT, request("OR", List.of("SG", "HK"), List.of("Tech"), "note", "alice"), "op");

        List<CalloutResponse> callouts = service.getCallouts(ASSMT);
        assertThat(callouts).hasSize(1);
        CalloutResponse c = callouts.get(0);
        assertThat(c.getRiskArea()).isEqualTo("OR");
        assertThat(c.getLocations()).containsExactly("SG", "HK");
        assertThat(c.getBizUnits()).containsExactly("Tech");
        assertThat(c.getComment()).isEqualTo("note");
        assertThat(c.getDeleted()).isFalse();
        // On create the SME both owns and is the last modifier.
        assertThat(c.getSme()).isEqualTo("alice");
        assertThat(c.getLastModifiedBy()).isEqualTo("alice");
    }

    @Test
    void getCallouts_noCallouts_returnsEmptyList() {
        assertThat(service.getCallouts(ASSMT)).isEmpty();
    }

    @Test
    void createCallout_unknownAssessment_throwsNotFound() {
        assertThatThrownBy(() ->
                service.createCallout(9999L, request("OR", List.of("SG"), List.of("Tech"), "c", "a"), "op"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("9999");
    }

    // ── update ──────────────────────────────────────────────────────────────────

    @Test
    void updateCallout_shiftsSmeOwnership_andUpdatesFields() {
        service.createCallout(ASSMT, request("OR", List.of("SG"), List.of("Tech"), "first", "alice"), "op");
        long id = service.getCallouts(ASSMT).get(0).getId();

        service.updateCallout(ASSMT, id, request("CR", List.of("IN"), List.of("Ops"), "second", "bob"), "op");

        CalloutResponse c = service.getCallouts(ASSMT).get(0);
        assertThat(c.getRiskArea()).isEqualTo("CR");
        assertThat(c.getLocations()).containsExactly("IN");
        assertThat(c.getBizUnits()).containsExactly("Ops");
        assertThat(c.getComment()).isEqualTo("second");
        // New SME becomes the owner; the previous owner shifts into last-modified.
        assertThat(c.getSme()).isEqualTo("bob");
        assertThat(c.getLastModifiedBy()).isEqualTo("alice");
    }

    @Test
    void updateCallout_unknownCallout_throwsNotFound() {
        assertThatThrownBy(() ->
                service.updateCallout(ASSMT, 8888L, request("OR", List.of("SG"), List.of("Tech"), "c", "a"), "op"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("8888");
    }

    @Test
    void updateCallout_calloutBelongsToDifferentAssessment_throwsNotFound() {
        service.createCallout(ASSMT, request("OR", List.of("SG"), List.of("Tech"), "c", "a"), "op");
        long id = service.getCallouts(ASSMT).get(0).getId();

        assertThatThrownBy(() ->
                service.updateCallout(OTHER_ASSMT, id, request("OR", List.of("SG"), List.of("Tech"), "c", "a"), "op"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("does not belong");
    }

    // ── soft delete ───────────────────────────────────────────────────────────

    @Test
    void deleteCallout_softDeletes_soItDropsFromActiveCallouts() {
        service.createCallout(ASSMT, request("OR", List.of("SG"), List.of("Tech"), "c", "a"), "op");
        long id = service.getCallouts(ASSMT).get(0).getId();

        service.deleteCallout(ASSMT, id);

        assertThat(service.getCallouts(ASSMT)).isEmpty();
        // The row is retained (soft delete), just flagged.
        Integer flagged = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_callout WHERE id=? AND DEL_FLG=TRUE", Integer.class, id);
        assertThat(flagged).isEqualTo(1);
    }

    @Test
    void deleteCallout_unknownAssessment_throwsNotFound() {
        assertThatThrownBy(() -> service.deleteCallout(9999L, 1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("9999");
    }

    /** Builds a {@link CalloutRequest} via Jackson (the DTO has no setters or builder). */
    private CalloutRequest request(String riskArea, List<String> locations, List<String> bizUnits,
                                   String comment, String sme) {
        try {
            return MAPPER.convertValue(
                    java.util.Map.of("riskArea", riskArea, "locations", locations,
                            "bizUnits", bizUnits, "comment", comment, "sme", sme),
                    CalloutRequest.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Failed to build CalloutRequest", e);
        }
    }
}
