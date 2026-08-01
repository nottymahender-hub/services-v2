package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.AssmtDetailCommentaryResponse;
import com.dbs.mot.grc.dto.AssmtDetailResponse;
import com.dbs.mot.grc.dto.LandscapeAssmtDetailItem;
import com.dbs.mot.grc.dto.LandscapeAssmtDetailSummary;
import com.dbs.mot.grc.dto.OverlayResponse;
import com.dbs.mot.grc.dto.SaveAssmtDetailOverlayNRRRequest;
import com.dbs.mot.grc.dto.SaveCommentaryRequest;
import com.dbs.mot.grc.exception.ConflictException;
import com.dbs.mot.grc.exception.NotFoundException;
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
 * Dedicated tests for {@link LandscapeAssmtDetailsService} against H2 — the list drill-down
 * ({@code fetchByAssmtId}), the single-row drill-down ({@code fetchDetailById}) with its
 * current/previous/live {@code fact_orl} snapshots, and the {@code save()}-based overlay save
 * with its status and not-found guards.
 *
 * <h3>Seed data</h3>
 * <ul>
 *   <li>Dim 1 "Alpha": RISK_AREA JSON maps "OR" → group "Cyber" clusters [C1,C2]; BIZ_UNIT_LVL=2.</li>
 *   <li>Assmt 40 (biz_dt 2024-06-01) is the previous assessment of assmt 41 (biz_dt 2024-07-01).</li>
 *   <li>fact_orl: OR/Tech/SG on 2024-07-01 (CAL 'Low') and 2024-06-01 (CAL 'Med Low').</li>
 *   <li>Detail 400 (assmt 40, OVRLY 'High') and 401 (assmt 41, Open, no overlay) share the
 *       dimension key (OR, Tech, '', '', SG); 402 (Locked, distinct location) is the conflict case.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class LandscapeAssmtDetailsServiceTest {

    @Autowired LandscapeAssmtDetailsService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM orl_lndscp_callout");
        jdbc.execute("DELETE FROM orl_lndscp_assmt_details");
        jdbc.execute("DELETE FROM orl_lndscp_assmt");
        jdbc.execute("DELETE FROM orl_lndscp_dim");
        jdbc.execute("DELETE FROM fact_orl");

        jdbc.execute("""
                INSERT INTO orl_lndscp_dim (id,CONFIG_ID,LNDSCP_NM,EFFECT_START_DT,VERSION,
                    RISK_AREA,BIZ_UNITS,BIZ_UNIT_LVL,LOCATIONS,CREATED_BY)
                VALUES(1,'CFG001','Alpha',DATE '2024-01-01',1,
                    '[{"groupName":"Cyber","isGroup":true,"riskAreas":[{"riskArea":"OR","riskClusters":["C1","C2"]}]}]',
                    'Tech',2,'SG','seed')
                """);
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,CREATED_BY,CREATE_DT_TM) "
                + "VALUES(40,1,'Q4-2023',DATE '2024-06-01','Closed','seed',TIMESTAMP '2024-06-01 00:00:00')");
        jdbc.execute("INSERT INTO orl_lndscp_assmt(id,LNDSCP_NUM,ASSEMT_PERIOD,biz_dt,status,PREV_ASSMT_NUM,CREATED_BY,CREATE_DT_TM) "
                + "VALUES(41,1,'Q1-2024',DATE '2024-07-01','Open',40,'seed',TIMESTAMP '2024-07-01 00:00:00')");

        insertFact("2024-07-01", "Low", "Satisfactory to Good", "current commentary");
        insertFact("2024-06-01", "Med Low", "Good", "june commentary");

        // Previous (assmt 40) matched row carries an overlay → prevAssmtFinalNRR = 'High'.
        insertDetail(400, 40, "L2", "High", "Completed");
        // Current (assmt 41) open row, no overlay.
        insertDetail(401, 41, "L2", null, "Open");
        // A non-Open row (located by primary key, so its parent assessment is irrelevant to the
        // save) → overlay save must be rejected with 409. Kept under assmt 40 with a distinct
        // LOCATION so it does not appear among assmt 41's detail rows.
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,STATUS,CREATED_BY)
                VALUES(402,40,'OR','Tech','HK','L2','Locked','seed')
                """);
    }

    /** Detail row on the shared (OR, Tech, '', '', SG) dimension key. */
    private void insertDetail(long id, long assmtId, String category, String ovrly, String status) {
        jdbc.execute("""
                INSERT INTO orl_lndscp_assmt_details
                    (id,lndscp_assmt_id,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,OVRLY_NET_RISK_RTNG,STATUS,CREATED_BY)
                VALUES(%d,%d,'OR','Tech','SG','%s',%s,'%s','seed')
                """.formatted(id, assmtId, category, ovrly == null ? "NULL" : "'" + ovrly + "'", status));
    }

    private void insertFact(String bizDt, String cal, String ctrl, String commentary) {
        jdbc.execute("""
                INSERT INTO fact_orl
                    (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,CAL_NET_RISK_RTNG,CTRL_EFF_RTN,COMMENTARY)
                VALUES(DATE '%s','OR','Tech','SG','L2','%s','%s','%s')
                """.formatted(bizDt, cal, ctrl, commentary));
    }

    // ── fetchByAssmtId (list drill-down) ────────────────────────────────────────

    @Test
    void fetchByAssmtId_populatesHeaderAndEnrichedItem() {
        LandscapeAssmtDetailSummary summary = service.fetchByAssmtId(41L);

        assertThat(summary.getLndscpName()).isEqualTo("Alpha");
        assertThat(summary.getLndscpAssmtId()).isEqualTo(41L);
        assertThat(summary.getLndscpAssmtStatus()).isEqualTo("Open");
        // lastRefreshed = latest fact_orl biz_dt across the table.
        assertThat(summary.getLndscpLastRefreshed()).isEqualTo(LocalDate.parse("2024-07-01"));
        assertThat(summary.getAssessments()).hasSize(1);

        LandscapeAssmtDetailItem item = summary.getAssessments().get(0);
        assertThat(item.getId()).isEqualTo(401L);
        assertThat(item.getRiskArea()).isEqualTo("OR");
        assertThat(item.getGroupName()).isEqualTo("Cyber");
        assertThat(item.getRiskClusters()).containsExactly("C1", "C2");
        assertThat(item.getBu()).isEqualTo("Tech");     // BIZ_UNIT_LVL=2 → ORL_BU_NM_L2
        assertThat(item.getLocation()).isEqualTo("SG");
        assertThat(item.getNrrCalculated()).isEqualTo("Low");
        assertThat(item.getNrr()).isEqualTo("Low"); // no overlay → falls back to calculated
        assertThat(item.getNrrOverlaid()).isEqualTo("N");
        assertThat(item.getCtrlEffRtn()).isEqualTo("Satisfactory to Good");
        // Previous assessment's matched row (400) had OVRLY 'High'.
        assertThat(item.getPrevAssmtFinalNRR()).isEqualTo("High");
    }

    @Test
    void fetchByAssmtId_unknownAssessment_throwsNotFound() {
        assertThatThrownBy(() -> service.fetchByAssmtId(9999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("9999");
    }

    @Test
    void fetchByAssmtId_ignoresFactsNotMatchingTheAssessmentsDetailKeys() {
        // An unrelated fact on the same current biz_dt (2024-07-01) whose dimension matches no
        // detail row of assmt 41. The targeted (semi-join) fetch must not pick it up: the single
        // item still reflects only the Tech/SG fact, and no phantom item appears.
        jdbc.execute("""
                INSERT INTO fact_orl
                    (biz_dt,RISK_AREA,ORL_BU_NM_L2,LOCATION,category,CAL_NET_RISK_RTNG,CTRL_EFF_RTN,COMMENTARY)
                VALUES(DATE '2024-07-01','OR','Zzz','XX','L2','High','Poor/Fail','unrelated')
                """);

        LandscapeAssmtDetailSummary summary = service.fetchByAssmtId(41L);

        assertThat(summary.getAssessments()).hasSize(1);
        LandscapeAssmtDetailItem item = summary.getAssessments().get(0);
        assertThat(item.getId()).isEqualTo(401L);
        // Still the Tech/SG fact's values — the unrelated 'Zzz/XX' fact was not fetched.
        assertThat(item.getNrrCalculated()).isEqualTo("Low");
        assertThat(item.getCtrlEffRtn()).isEqualTo("Satisfactory to Good");
    }

    @Test
    void fetchByAssmtId_noPreviousAssessment_leavesPrevRatingNull() {
        // Assmt 40 has no PREV_ASSMT_NUM, so no previous facts are fetched and every item's
        // prevAssmtFinalNRR is null. Its current facts come from its own biz_dt (2024-06-01).
        LandscapeAssmtDetailSummary summary = service.fetchByAssmtId(40L);

        assertThat(summary.getAssessments()).hasSize(2);
        assertThat(summary.getAssessments())
                .allSatisfy(i -> assertThat(i.getPrevAssmtFinalNRR()).isNull());
        // The SG row (400) matches the 2024-06-01 fact (CAL 'Med Low'); it also has OVRLY 'High'.
        LandscapeAssmtDetailItem sgRow = summary.getAssessments().stream()
                .filter(i -> i.getId() == 400L).findFirst().orElseThrow();
        assertThat(sgRow.getNrrCalculated()).isEqualTo("Med Low");
        assertThat(sgRow.getNrr()).isEqualTo("High");
        assertThat(sgRow.getNrrOverlaid()).isEqualTo("Y");
    }

    // ── fetchDetailById (single-row drill-down) ─────────────────────────────────

    @Test
    void fetchDetailById_buildsCurrentPreviousAndLiveSnapshots() {
        AssmtDetailResponse response = service.fetchDetailById(41L, 401L);

        assertThat(response.getId()).isEqualTo(401L);
        assertThat(response.getLandscapeAssessmentId()).isEqualTo(41L);
        assertThat(response.getRiskArea()).isEqualTo("OR");
        assertThat(response.getLastRefreshed()).isEqualTo(LocalDate.parse("2024-07-01"));

        // Current month (assmt 41 biz_dt 2024-07-01): CAL 'Low', no overlay.
        assertThat(response.getCurrentMonthNRRDetails().getNrrCalculated()).isEqualTo("Low");
        assertThat(response.getCurrentMonthNRRDetails().getNrr()).isEqualTo("Low");
        assertThat(response.getCurrentMonthNRRDetails().getNrrOverlaid()).isEqualTo("N");
        assertThat(response.getCurrentMonthNRRDetails().getCommentry()).isEqualTo("current commentary");

        // Previous month via PREV_ASSMT_NUM → matched row 400 (OVRLY 'High'), prev fact CAL 'Med Low'.
        assertThat(response.getPrevMonthNRRDetails()).isNotNull();
        assertThat(response.getPrevMonthNRRDetails().getNrrCalculated()).isEqualTo("Med Low");
        assertThat(response.getPrevMonthNRRDetails().getNrr()).isEqualTo("High");

        // Live snapshot: latest biz_dt (2024-07-01) equals the assessment's biz_dt, so live reuses
        // the current-month fact/metrics — same values as current.
        assertThat(response.getLiveNRRDetails()).isNotNull();
        assertThat(response.getLiveNRRDetails().getNrr()).isEqualTo("Low");
        assertThat(response.getLiveNRRDetails().getLastRefreshed()).isEqualTo(LocalDate.parse("2024-07-01"));
    }

    @Test
    void fetchDetailById_assessmentOlderThanLatest_livesFromMaxBizDt() {
        // Assmt 40's biz_dt is 2024-06-01 but the latest fact biz_dt is 2024-07-01, so the live
        // block is fetched separately for 2024-07-01 (different from current) — exercises the
        // non-reuse branch. Detail 400 (OR/Tech/SG): current CAL 'Med Low', live CAL 'Low'.
        AssmtDetailResponse response = service.fetchDetailById(40L, 400L);

        assertThat(response.getLastRefreshed()).isEqualTo(LocalDate.parse("2024-07-01"));
        assertThat(response.getCurrentMonthNRRDetails().getNrrCalculated()).isEqualTo("Med Low");
        assertThat(response.getLiveNRRDetails()).isNotNull();
        assertThat(response.getLiveNRRDetails().getNrr()).isEqualTo("Low");
        assertThat(response.getLiveNRRDetails().getLastRefreshed()).isEqualTo(LocalDate.parse("2024-07-01"));
        // Assmt 40 has no previous assessment.
        assertThat(response.getPrevMonthNRRDetails()).isNull();
    }

    @Test
    void fetchDetailById_noFacts_liveIsNull() {
        jdbc.execute("DELETE FROM fact_orl");
        AssmtDetailResponse response = service.fetchDetailById(41L, 401L);

        assertThat(response.getLastRefreshed()).isNull();
        assertThat(response.getLiveNRRDetails()).isNull();
        assertThat(response.getCurrentMonthNRRDetails().getNrrCalculated()).isNull();
    }

    @Test
    void fetchDetailById_unknownDetail_throwsNotFound() {
        assertThatThrownBy(() -> service.fetchDetailById(41L, 8888L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("8888");
    }

    // ── saveOverlay ─────────────────────────────────────────────────────────────

    @Test
    void saveOverlay_persistsOverlay_stampsUpdatedBy_andLeavesCommentaryUntouched() {
        // Seed an existing revised commentary; the overlay save must not touch it.
        jdbc.execute("UPDATE orl_lndscp_assmt_details SET REVISED_COMMENTARY='pre-existing' WHERE id=401");
        SaveAssmtDetailOverlayNRRRequest req = new SaveAssmtDetailOverlayNRRRequest();
        req.setOverlaidNRR("Low");
        req.setOverlayJstfkn("overlay reason");

        service.saveOverlay(41L, 401L, req, "auditor");

        assertThat(column(401, "OVRLY_NET_RISK_RTNG")).isEqualTo("Low");
        assertThat(column(401, "OVRLY_JSTFKN")).isEqualTo("overlay reason");
        assertThat(column(401, "UPDATED_BY")).isEqualTo("auditor");
        // Overlay does not save commentary; the pre-existing value is preserved.
        assertThat(column(401, "REVISED_COMMENTARY")).isEqualTo("pre-existing");
        // UPDATE_DT_TM is DB-managed (ON UPDATE CURRENT_TIMESTAMP).
        Integer stamped = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details WHERE id=401 AND UPDATE_DT_TM IS NOT NULL",
                Integer.class);
        assertThat(stamped).isEqualTo(1);
    }

    @Test
    void saveOverlay_blankFields_clearOverlay() {
        SaveAssmtDetailOverlayNRRRequest req = new SaveAssmtDetailOverlayNRRRequest();
        req.setOverlaidNRR(null);
        req.setOverlayJstfkn(null);

        service.saveOverlay(41L, 401L, req, "auditor");

        assertThat(column(401, "OVRLY_NET_RISK_RTNG")).isNull();
        assertThat(column(401, "OVRLY_JSTFKN")).isNull();
    }

    @Test
    void saveOverlay_detailNotOpen_throwsConflict() {
        SaveAssmtDetailOverlayNRRRequest req = new SaveAssmtDetailOverlayNRRRequest();
        assertThatThrownBy(() -> service.saveOverlay(41L, 402L, req, "auditor"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Open");
    }

    @Test
    void saveOverlay_unknownAssessment_throwsNotFound() {
        assertThatThrownBy(() -> service.saveOverlay(9999L, 401L, new SaveAssmtDetailOverlayNRRRequest(), "auditor"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("9999");
    }

    @Test
    void saveOverlay_unknownDetail_throwsNotFound() {
        assertThatThrownBy(() -> service.saveOverlay(41L, 8888L, new SaveAssmtDetailOverlayNRRRequest(), "auditor"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("8888");
    }

    @Test
    void saveOverlay_returnsResponse_andRecomputesRiskRatingChange_whenOverlayChanges() {
        // Detail 401 starts with no overlay. Previous assessment 40's matched row 400 has OVRLY 'High'
        // → previous final 'High'; new overlay 'Low' → less severe → Improved.
        SaveAssmtDetailOverlayNRRRequest req = new SaveAssmtDetailOverlayNRRRequest();
        req.setOverlaidNRR("Low");
        req.setOverlayJstfkn("justified");

        OverlayResponse resp = service.saveOverlay(41L, 401L, req, "auditor");

        assertThat(resp.getLndscpAssmtId()).isEqualTo(41L);
        assertThat(resp.getAssmtDetailId()).isEqualTo(401L);
        assertThat(resp.getOverlaidNRR()).isEqualTo("Low");
        assertThat(resp.getStatus()).isEqualTo("Open");
        assertThat(resp.getRiskRatingChange()).isEqualTo("Improved");
        assertThat(column(401, "RISK_RTNG_CHGE")).isEqualTo("Improved");
    }

    @Test
    void saveOverlay_overlayUnchanged_leavesRiskRatingChangeUntouched() {
        // Seed an existing change; saving with the same (absent) overlay must not recompute it.
        jdbc.execute("UPDATE orl_lndscp_assmt_details SET RISK_RTNG_CHGE='Deteriorated' WHERE id=401");
        SaveAssmtDetailOverlayNRRRequest req = new SaveAssmtDetailOverlayNRRRequest();   // overlaidNRR stays null (unchanged)

        OverlayResponse resp = service.saveOverlay(41L, 401L, req, "auditor");

        assertThat(resp.getRiskRatingChange()).isEqualTo("Deteriorated");
        assertThat(column(401, "RISK_RTNG_CHGE")).isEqualTo("Deteriorated");
    }

    // ── saveCommentary ──────────────────────────────────────────────────────────

    @Test
    void saveCommentary_updatesCommentary_stampsReviserAndTimestamp_andReturnsThem() {
        SaveCommentaryRequest req = new SaveCommentaryRequest();
        req.setRevisedCommentry("Analyst note");

        AssmtDetailCommentaryResponse resp = service.saveCommentary(41L, 401L, req, "auditor");

        assertThat(resp.getLndscpAssmtId()).isEqualTo(41L);
        assertThat(resp.getAssmtDetailId()).isEqualTo(401L);
        assertThat(resp.getRevisedCommentary()).isEqualTo("Analyst note");
        assertThat(resp.getCommentaryRevisedBy()).isEqualTo("auditor");
        assertThat(resp.getCommentaryRevisedAt()).isNotNull();   // stamped (surfaced in SGT)
        assertThat(column(401, "REVISED_COMMENTARY")).isEqualTo("Analyst note");
        assertThat(column(401, "COMMENTARY_REVISED_BY")).isEqualTo("auditor");
        assertThat(column(401, "UPDATED_BY")).isEqualTo("auditor");
        Integer atSet = jdbc.queryForObject(
                "SELECT COUNT(*) FROM orl_lndscp_assmt_details WHERE id=401 AND COMMENTARY_REVISED_AT IS NOT NULL",
                Integer.class);
        assertThat(atSet).isEqualTo(1);
    }

    @Test
    void saveCommentary_detailNotOpen_throwsConflict() {
        assertThatThrownBy(() -> service.saveCommentary(40L, 402L, new SaveCommentaryRequest(), "auditor"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Open");
    }

    @Test
    void saveCommentary_unknownAssessment_throwsNotFound() {
        assertThatThrownBy(() -> service.saveCommentary(9999L, 401L, new SaveCommentaryRequest(), "auditor"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("9999");
    }

    private String column(long id, String col) {
        return jdbc.queryForObject(
                "SELECT " + col + " FROM orl_lndscp_assmt_details WHERE id=" + id, String.class);
    }
}
