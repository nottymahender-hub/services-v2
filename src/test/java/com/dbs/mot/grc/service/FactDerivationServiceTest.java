package com.dbs.mot.grc.service;

import com.dbs.mot.grc.dto.DerivedDetailColumns;
import com.dbs.mot.grc.dto.MatchedFactRows;
import com.dbs.mot.grc.entity.InaFactOrl;
import com.dbs.mot.grc.entity.IncFactOrl;
import com.dbs.mot.grc.entity.KriFactOrl;
import com.dbs.mot.grc.entity.RcsaFactOrl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link FactDerivationService} — plain (no Spring context), exercising
 * every module-present/absent permutation and the worst-of-modules rating logic.
 */
class FactDerivationServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final FactDerivationService service = new FactDerivationService(mapper);

    private IncFactOrl inc(String nrr) {
        return IncFactOrl.builder().netRiskRtng(nrr)
                .incIsSinpCountL3mMtd(1).incIsMiCountL3mMtd(2).incIsGorcCountL3mMtd(3)
                .incIsMinReportableCountL3mMtd(4).incTimeToDetectSumL11mMtd(5).incCountL11mMtd(6)
                .build();
    }

    private InaFactOrl ina(String nrr) {
        return InaFactOrl.builder().netRiskRtng(nrr)
                .issueRatingHighCount(1).issueRatingMediumCount(2).issueTypeRegulatoryCount(3)
                .issueTypeAuditCount(4).issueTypeOthersCount(5).issueOpenCount(6)
                .issueClosedCountL3mMtd(7).issueRepeatedCount(8).build();
    }

    private KriFactOrl kri(String nrr) {
        return KriFactOrl.builder().netRiskRtng(nrr)
                .kriSustainedRed3mOrQuarterlyRedCount(1).kriSustainedRed2mCount(2)
                .kriSustainedRedAmber4mOrQuarterlyAmberCount(3).kriAmberSustainedRedAmber3mCount(4)
                .kriRedCount(5).kriAmberCount(6).kriGreenCount(7).build();
    }

    private RcsaFactOrl rcsa(String nrr) {
        return RcsaFactOrl.builder().netRiskRtng(nrr)
                .rcsaHighRiskProportion(1).rcsaMedhighRiskProportion(2)
                .rcsaMedlowRiskProportion(3).rcsaLowRiskProportion(4).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @Test
    void allModulesPresent_grcMetricsHasFourKeys_andWorstRating() throws Exception {
        MatchedFactRows matched = new MatchedFactRows(
                inc("Low"), ina("Med Low"), kri("High"), rcsa("Med High"));

        DerivedDetailColumns d = service.derive(matched);

        assertThat(d.calNetRiskRtng()).isEqualTo("High");
        assertThat(d.commentary()).isNull();
        assertThat(d.ctrlEffRtn()).isNull();

        Map<String, Object> root = parse(d.grcMetrics());
        assertThat(root).containsOnlyKeys("INC", "INA", "KRI", "RCSA");

        Map<String, Object> incNode = (Map<String, Object>) root.get("INC");
        assertThat(incNode.get("nrr")).isEqualTo("Low");
        Map<String, Object> sinp = (Map<String, Object>) incNode.get("inc_is_sinp_count_l3m_mtd");
        assertThat(sinp.get("count")).isEqualTo(1);
        assertThat(sinp.get("riskRatingChange")).isEqualTo("Improved");
    }

    @Test
    void subsetOfModules_onlyThoseKeysAppear() throws Exception {
        MatchedFactRows matched = new MatchedFactRows(inc("Low"), null, kri("Med Low"), null);

        DerivedDetailColumns d = service.derive(matched);

        Map<String, Object> root = parse(d.grcMetrics());
        assertThat(root).containsOnlyKeys("INC", "KRI");
        assertThat(d.calNetRiskRtng()).isEqualTo("Med Low");
    }

    @Test
    void noModules_grcMetricsAndRatingNull() {
        DerivedDetailColumns d = service.derive(new MatchedFactRows(null, null, null, null));

        assertThat(d.grcMetrics()).isNull();
        assertThat(d.calNetRiskRtng()).isNull();
    }

    @Test
    void worstOf_picksHighestSeverity_ignoringOrderOfInput() {
        DerivedDetailColumns d = service.derive(new MatchedFactRows(
                inc("Med High"), ina("Low"), kri("Med Low"), rcsa("Med High")));
        assertThat(d.calNetRiskRtng()).isEqualTo("Med High");
    }

    @Test
    void unrecognisedRating_isIgnoredInWorstOf() {
        // 'Critical' is not a valid band → ignored; worst of the rest is 'Med Low'
        DerivedDetailColumns d = service.derive(new MatchedFactRows(
                inc("Critical"), ina("Low"), kri("Med Low"), null));
        assertThat(d.calNetRiskRtng()).isEqualTo("Med Low");
    }

    @Test
    void allRatingsUnrecognised_ratingIsNull_butGrcMetricsStillBuilt() throws Exception {
        DerivedDetailColumns d = service.derive(new MatchedFactRows(inc("???"), null, null, null));
        assertThat(d.calNetRiskRtng()).isNull();
        Map<String, Object> root = parse(d.grcMetrics());
        assertThat(root).containsOnlyKeys("INC");
    }
}
