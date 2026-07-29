package com.dbs.mot.grc.util;

import com.dbs.mot.grc.entity.ModuleFact;
import com.dbs.mot.grc.enums.NetRiskRating;
import com.dbs.mot.grc.util.ModuleRiskRatingChanges.ModuleChange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ModuleRiskRatingChanges} — the MODULE_RISK_RTNG_CHGE builder/parser:
 * NRR-based module change, neutral per-metric change, and lenient parsing.
 */
class ModuleRiskRatingChangesTest {

    private final ModuleRiskRatingChanges util = new ModuleRiskRatingChanges();

    /** Minimal {@link ModuleFact} test double: a net risk rating + an ordered metric map. */
    private static ModuleFact fact(NetRiskRating nrr, Map<String, Object> metrics) {
        return new ModuleFact() {
            @Override
            public NetRiskRating getNetRiskRtng() {
                return nrr;
            }

            @Override
            public Map<String, Object> metrics() {
                return metrics;
            }
        };
    }

    private static Map<String, Object> metrics(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    @Test
    void build_derivesModuleChange_andNeutralMetricChanges() {
        // Module NRR improved (High → Low); count decreased (10 → 4); proportion increased.
        ModuleFact current = fact(NetRiskRating.LOW,
                metrics("count", 4, "prop", new BigDecimal("50.00")));
        ModuleFact previous = fact(NetRiskRating.HIGH,
                metrics("count", 10, "prop", new BigDecimal("25.00")));

        String json = util.build(Map.of("INC", current), Map.of("INC", previous));

        assertThat(json).contains("\"INC\"")
                .contains("\"riskRatingChange\":\"Improved\"")
                .contains("\"count\":\"Decreased\"")
                .contains("\"prop\":\"Increased\"");
    }

    @Test
    void build_noPreviousFact_yieldsNotApplicableEverywhere() {
        ModuleFact current = fact(NetRiskRating.HIGH, metrics("count", 7));

        String json = util.build(Map.of("KRI", current), Map.of());

        assertThat(json).contains("\"riskRatingChange\":\"N.A\"").contains("\"count\":\"N.A\"");
    }

    @Test
    void build_stableMetric_whenValuesEqual() {
        ModuleFact current = fact(NetRiskRating.HIGH, metrics("count", 5));
        ModuleFact previous = fact(NetRiskRating.HIGH, metrics("count", 5));

        String json = util.build(Map.of("RCSA", current), Map.of("RCSA", previous));

        // Equal NRR → Stable module change; equal metric → Stable.
        assertThat(json).contains("\"riskRatingChange\":\"Stable\"").contains("\"count\":\"Stable\"");
    }

    @Test
    void build_omitsModulesWithNoCurrentFact_andReturnsNullWhenAllAbsent() {
        Map<String, ModuleFact> current = new LinkedHashMap<>();
        current.put("INC", null);
        current.put("KRI", null);

        assertThat(util.build(current, Map.of())).isNull();
    }

    @Test
    void build_skipsNullCurrentModule_keepsPopulatedOne() {
        Map<String, ModuleFact> current = new LinkedHashMap<>();
        current.put("INC", null);
        current.put("KRI", fact(NetRiskRating.MED_LOW, metrics("count", 1)));

        String json = util.build(current, Map.of());

        assertThat(json).contains("\"KRI\"").doesNotContain("\"INC\"");
    }

    @Test
    void parse_extractsModuleAndMetricChangesPerModule() {
        String json = "{\"RCSA\":{\"riskRatingChange\":\"Improved\",\"count\":\"Decreased\"},"
                + "\"INC\":{\"riskRatingChange\":\"Deteriorated\"}}";

        Map<String, ModuleChange> changes = util.parse(json);

        assertThat(changes.get("RCSA").riskRatingChangeOrNa()).isEqualTo("Improved");
        assertThat(changes.get("RCSA").metricChangeOrNa("count")).isEqualTo("Decreased");
        assertThat(changes.get("INC").riskRatingChangeOrNa()).isEqualTo("Deteriorated");
        // A metric not present in the document resolves to N.A.
        assertThat(changes.get("INC").metricChangeOrNa("count")).isEqualTo("N.A");
    }

    @Test
    void parse_nullOrBlankOrUnparseable_isEmpty() {
        assertThat(util.parse(null)).isEmpty();
        assertThat(util.parse("   ")).isEmpty();
        assertThat(util.parse("not json")).isEmpty();
    }

    @Test
    void moduleChangeNone_resolvesEverythingToNa() {
        assertThat(ModuleChange.NONE.riskRatingChangeOrNa()).isEqualTo("N.A");
        assertThat(ModuleChange.NONE.metricChangeOrNa("anything")).isEqualTo("N.A");
    }

    @Test
    void roundTrip_buildThenParse_surfacesModuleAndMetricChange() {
        ModuleFact current = fact(NetRiskRating.LOW, metrics("count", 5));
        ModuleFact previous = fact(NetRiskRating.HIGH, metrics("count", 1));

        Map<String, ModuleChange> changes = util.parse(
                util.build(Map.of("INC", current), Map.of("INC", previous)));

        assertThat(changes.get("INC").riskRatingChangeOrNa()).isEqualTo("Improved");
        assertThat(changes.get("INC").metricChangeOrNa("count")).isEqualTo("Increased");
    }
}
