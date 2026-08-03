package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.AssmtGenerationResponse;
import com.dbs.mot.grc.entity.OrlLndscpDim;
import com.dbs.mot.grc.exception.ConflictException;
import com.dbs.mot.grc.exception.NoFactDataException;
import com.dbs.mot.grc.repository.OrlLndscpDimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated tests for {@link LandscapeAssmtGenerationService} against H2 — the reported-period
 * calculation, {@code biz_dt} resolution (latest {@code fact_orl} date in the month, never a
 * synthetic fallback), the already-exists/no-data guards and their ordering, row expansion into
 * L/grp/loc categories, BU-hierarchy resolution, and {@code PREV_ASSMT_NUM} linkage.
 *
 * <h3>Seed data</h3>
 * <ul>
 *   <li>'Alpha' (id 1, level 2): 1 risk area x ((2 BU x 2 loc) + 2 grp + 2 loc) = 8 detail rows.</li>
 *   <li>{@code orl_biz_unit}: Technology, Operations (both level 2).</li>
 * </ul>
 *
 * <p>As-of date is fixed ({@code 2026-08-15}, not {@code LocalDate.now()}) since this service has
 * no future-date guard of its own (that check lives in the controller) — a hardcoded date keeps
 * every test fully deterministic.
 */
@SpringBootTest
@ActiveProfiles("test")
class LandscapeAssmtGenerationServiceTest {

    // As-of 2026-08-15 -> reports July 2026 (M-1), links to June 2026 (M-2).
    private static final LocalDate AS_OF_DATE = LocalDate.of(2026, 8, 15);
    private static final String REPORTED_PERIOD = "July 2026";
    private static final String PRIOR_PERIOD = "June 2026";

    @Autowired LandscapeAssmtGenerationService service;
    @Autowired OrlLndscpDimRepository dimRepository;
    @Autowired JdbcTemplate jdbc;

    private OrlLndscpDim dim;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        jdbc.execute("DELETE FROM orl_biz_unit");
        jdbc.execute("DELETE FROM fact_orl");

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '[{"groupName":"Conduct","isGroup":false,"riskAreas":[{"riskArea":"Market Abuse","riskClusters":["OR"]}]}]',
                    'Technology,Operations',2,'SG,CN','seed')
                """);
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,CREATED_BY) VALUES(10,'Technology',2,'Technology','seed')");
        jdbc.execute("INSERT INTO orl_biz_unit(BU_NUM,BU_NM,LVL_OF_HIER,ORL_BU_NM_L2,CREATED_BY) VALUES(11,'Operations',2,'Operations','seed')");

        dim = dimRepository.findById(1L).orElseThrow();
    }

    private void insertFactOrlOn(String bizDt) {
        jdbc.execute("""
                INSERT INTO fact_orl (biz_dt, RISK_AREA, category, CAL_NET_RISK_RTNG)
                VALUES (DATE '%s', 'Any', 'L2', 'Low')
                """.formatted(bizDt));
    }

    // ── Happy path ──────────────────────────────────────────────────────────────

    @Test
    void generateForDim_happyPath_returnsResponseAndPersistsAggregate() {
        insertFactOrlOn("2026-07-15");

        AssmtGenerationResponse response = service.generateForDim(dim, AS_OF_DATE, "tester");

        assertThat(response.getLndscpAssmtId()).isNotNull();
        assertThat(response.getLndscpNm()).isEqualTo("Alpha");
        assertThat(response.getLndscpNum()).isEqualTo(1L);
        assertThat(response.getAssmtPeriod()).isEqualTo(REPORTED_PERIOD);
        assertThat(response.getDetailRowCount()).isEqualTo(8);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt WHERE id=? AND ASSEMT_PERIOD=? AND status='Open' AND CREATED_BY='tester'",
                Integer.class, response.getLndscpAssmtId(), REPORTED_PERIOD);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void generateForDim_bizDtIsLatestFactDateInMonth_notMonthEnd() {
        insertFactOrlOn("2026-07-10");
        insertFactOrlOn("2026-07-22");

        AssmtGenerationResponse response = service.generateForDim(dim, AS_OF_DATE, "tester");

        LocalDate bizDt = jdbc.queryForObject(
                "SELECT biz_dt FROM orl_lndscp_assmt WHERE id=?", LocalDate.class, response.getLndscpAssmtId());
        assertThat(bizDt).isEqualTo(LocalDate.of(2026, 7, 22));
    }

    @Test
    void generateForDim_writesThinRows_openStatusNoOverlay() {
        insertFactOrlOn("2026-07-15");

        AssmtGenerationResponse response = service.generateForDim(dim, AS_OF_DATE, "tester");

        Integer thinRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details WHERE lndscp_assmt_id=? "
                        + "AND STATUS='Open' AND OVRLY_NET_RISK_RTNG IS NULL",
                Integer.class, response.getLndscpAssmtId());
        assertThat(thinRows).isEqualTo(8);
    }

    @Test
    void generateForDim_expandsIntoLGrpAndLocCategories_emptyDimsAsBlank() {
        insertFactOrlOn("2026-07-15");

        AssmtGenerationResponse response = service.generateForDim(dim, AS_OF_DATE, "tester");
        Long assmtId = response.getLndscpAssmtId();

        assertRowCount(assmtId, "category='L2'", 4);      // 2 BU x 2 locations
        assertRowCount(assmtId, "category='grp_l2'", 2);  // 2 BU, no location
        assertRowCount(assmtId, "category='loc'", 2);     // 2 locations, no BU
        // Empty dimension columns are never null.
        assertRowCount(assmtId, "ORL_BU_NM_L2 IS NULL OR ORL_BU_NM_L3 IS NULL OR ORL_BU_NM_L4 IS NULL OR LOCATION IS NULL", 0);
    }

    @Test
    void generateForDim_resolvesBuHierarchy_level2() {
        insertFactOrlOn("2026-07-15");

        AssmtGenerationResponse response = service.generateForDim(dim, AS_OF_DATE, "tester");

        assertRowCount(response.getLndscpAssmtId(),
                "category='L2' AND ORL_BU_NM_L2='Technology' AND LOCATION='SG'", 1);
        assertRowCount(response.getLndscpAssmtId(),
                "category='L2' AND ORL_BU_NM_L2='Operations' AND LOCATION='CN'", 1);
    }

    @Test
    void generateForDim_unresolvedBu_keepsNameAtOwnLevel() {
        jdbc.execute("DELETE FROM orl_biz_unit WHERE BU_NM='Technology'");
        insertFactOrlOn("2026-07-15");

        AssmtGenerationResponse response = service.generateForDim(dim, AS_OF_DATE, "tester");

        // Technology unresolved -> still present with L2='Technology', empty L3/L4 (2 locations, L2 category only).
        assertRowCount(response.getLndscpAssmtId(),
                "ORL_BU_NM_L2='Technology' AND ORL_BU_NM_L3='' AND ORL_BU_NM_L4='' AND category='L2'", 2);
    }

    // ── Skip / exception conditions ────────────────────────────────────────────

    @Test
    void generateForDim_alreadyExists_throwsConflictException() {
        insertFactOrlOn("2026-07-15");
        service.generateForDim(dim, AS_OF_DATE, "tester");

        assertThatThrownBy(() -> service.generateForDim(dim, AS_OF_DATE, "tester"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Alpha")
                .hasMessageContaining(REPORTED_PERIOD);
    }

    @Test
    void generateForDim_noFactDataForReportedMonth_throwsNoFactDataException() {
        assertThatThrownBy(() -> service.generateForDim(dim, AS_OF_DATE, "tester"))
                .isInstanceOf(NoFactDataException.class)
                .hasMessageContaining(REPORTED_PERIOD);
    }

    @Test
    void generateForDim_alreadyExists_isCheckedBeforeBizDateResolution() {
        // Generate once with data present, then remove all fact_orl data and try again for the same
        // period. Even with zero fact data now, the duplicate-period conflict must win — proving the
        // already-exists check runs before biz_dt resolution, not after.
        insertFactOrlOn("2026-07-15");
        service.generateForDim(dim, AS_OF_DATE, "tester");
        jdbc.execute("DELETE FROM fact_orl");

        assertThatThrownBy(() -> service.generateForDim(dim, AS_OF_DATE, "tester"))
                .isInstanceOf(ConflictException.class);
    }

    // ── PREV_ASSMT_NUM linkage ──────────────────────────────────────────────────

    @Test
    void generateForDim_linksToPreviousMonthAssessment_whenExists() {
        jdbc.update("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(500,1,?,'Open','seed')",
                PRIOR_PERIOD);
        insertFactOrlOn("2026-07-15");

        AssmtGenerationResponse response = service.generateForDim(dim, AS_OF_DATE, "tester");

        Long prev = jdbc.queryForObject(
                "SELECT PREV_ASSMT_NUM FROM orl_lndscp_assmt WHERE id=?", Long.class, response.getLndscpAssmtId());
        assertThat(prev).isEqualTo(500L);
    }

    @Test
    void generateForDim_noPreviousAssessment_prevAssmtNumNull() {
        insertFactOrlOn("2026-07-15");

        AssmtGenerationResponse response = service.generateForDim(dim, AS_OF_DATE, "tester");

        Long prev = jdbc.queryForObject(
                "SELECT PREV_ASSMT_NUM FROM orl_lndscp_assmt WHERE id=?", Long.class, response.getLndscpAssmtId());
        assertThat(prev).isNull();
    }

    @Test
    void generateForDim_previousAssessmentOfDifferentLandscape_notLinked() {
        // A second landscape config row is needed as the FK target for LNDSCP_NUM=2's prior assessment.
        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,LOCATIONS,CREATED_BY)
                VALUES(2,'CFG002','Beta',DATE '2024-01-01',1,'[]','SG','seed')
                """);
        jdbc.update("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,status,CREATED_BY) VALUES(501,2,?,'Open','seed')",
                PRIOR_PERIOD);
        insertFactOrlOn("2026-07-15");

        AssmtGenerationResponse response = service.generateForDim(dim, AS_OF_DATE, "tester");

        Long prev = jdbc.queryForObject(
                "SELECT PREV_ASSMT_NUM FROM orl_lndscp_assmt WHERE id=?", Long.class, response.getLndscpAssmtId());
        assertThat(prev).isNull();
    }

    private void assertRowCount(long assmtId, String whereClause, int expected) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details WHERE lndscp_assmt_id=" + assmtId
                        + " AND (" + whereClause + ")", Integer.class);
        assertThat(count).as("rows matching [%s]", whereClause).isEqualTo(expected);
    }
}
