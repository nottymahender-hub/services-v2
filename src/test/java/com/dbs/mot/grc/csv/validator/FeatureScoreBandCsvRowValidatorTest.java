package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.FeatureScoreBandCsvRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureScoreBandCsvRowValidatorTest {

    private final FeatureScoreBandCsvRowValidator validator = new FeatureScoreBandCsvRowValidator();

    // ── INC ──────────────────────────────────────────────────────────────────

    @Test
    void inc_allAllowedFeatureNames_noErrors() {
        List<FeatureScoreBandCsvRow> rows = List.of(
            row("INC", "inc_is_sinp_count_l3m_mtd"),
            row("INC", "inc_is_mi_count_l3m_mtd"),
            row("INC", "inc_is_gorc_count_l3m_mtd_per_50_fte"),
            row("INC", "inc_is_min_reportable_count_l3m_mtd_per_50_fte"),
            row("INC", "inc_avg_time_to_detect_l11m_mtd")
        );
        assertThat(validator.validate(rows)).isEmpty();
    }

    @Test
    void inc_disallowedFeatureName_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(
            List.of(row("INC", "unknown_feature")));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("feature_name");
        assertThat(errors.get(0).getMessage()).contains("unknown_feature").contains("INC");
    }

    @Test
    void inc_duplicateFeatureName_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
            row("INC", "inc_is_sinp_count_l3m_mtd"),
            row("INC", "inc_is_sinp_count_l3m_mtd")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getRow()).isEqualTo(2);
        assertThat(errors.get(0).getMessage()).contains("inc_is_sinp_count_l3m_mtd");
    }

    // ── INA ──────────────────────────────────────────────────────────────────

    @Test
    void ina_allAllowedFeatureNames_noErrors() {
        List<FeatureScoreBandCsvRow> rows = List.of(
            row("INA", "issue_rating_high_count_per_50_fte"),
            row("INA", "issue_rating_medium_count_per_50_fte"),
            row("INA", "issue_type_regulatory_count_per_50_fte"),
            row("INA", "issue_type_audit_count_per_50_fte"),
            row("INA", "issue_type_others_count_per_50_fte"),
            row("INA", "issue_open_count_per_50_fte"),
            row("INA", "issue_closed_count_per_50_fte"),
            row("INA", "issue_repeated_count")
        );
        assertThat(validator.validate(rows)).isEmpty();
    }

    @Test
    void ina_disallowedFeatureName_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(
            List.of(row("INA", "issue_unknown")));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("issue_unknown").contains("INA");
    }

    @Test
    void ina_duplicateFeatureName_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
            row("INA", "issue_rating_high_count_per_50_fte"),
            row("INA", "issue_rating_high_count_per_50_fte")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getRow()).isEqualTo(2);
    }

    // ── RCSA ─────────────────────────────────────────────────────────────────

    @Test
    void rcsa_allAllowedFeatureNames_noErrors() {
        List<FeatureScoreBandCsvRow> rows = List.of(
            row("RCSA", "rcsa_weighted_avg_risk"),
            row("RCSA", "rcsa_high_medhigh_risk_vs_medlow_low_risk_proportion")
        );
        assertThat(validator.validate(rows)).isEmpty();
    }

    @Test
    void rcsa_disallowedFeatureName_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(
            List.of(row("RCSA", "rcsa_unknown")));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("rcsa_unknown").contains("RCSA");
    }

    @Test
    void rcsa_duplicateFeatureName_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
            row("RCSA", "rcsa_weighted_avg_risk"),
            row("RCSA", "rcsa_weighted_avg_risk")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getRow()).isEqualTo(2);
    }

    // ── KRI ──────────────────────────────────────────────────────────────────

    @Test
    void kri_allAllowedFeatureNames_noErrors() {
        List<FeatureScoreBandCsvRow> rows = List.of(
            row("KRI", "kri_sustained_red_3m_or_quarterly_red_proportion"),
            row("KRI", "kri_sustained_red_2m_proportion"),
            row("KRI", "kri_sustained_red_amber_4m_or_quarterly_amber_proportion"),
            row("KRI", "kri_amber_sustained_red_amber_3m_proportion")
        );
        assertThat(validator.validate(rows)).isEmpty();
    }

    @Test
    void kri_disallowedFeatureName_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(
            List.of(row("KRI", "kri_unknown")));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("kri_unknown").contains("KRI");
    }

    @Test
    void kri_duplicateFeatureName_addsError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
            row("KRI", "kri_sustained_red_2m_proportion"),
            row("KRI", "kri_sustained_red_2m_proportion")
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getRow()).isEqualTo(2);
    }

    // ── Cross-module ──────────────────────────────────────────────────────────

    @Test
    void sameFeatureName_differentModules_noError() {
        // feature_name uniqueness is per-module; same name across modules is fine
        // (contrived example using TPRM which has no restrictions)
        assertThat(validator.validate(List.of(
            row("INC", "inc_is_sinp_count_l3m_mtd"),
            row("TPRM", "inc_is_sinp_count_l3m_mtd")
        ))).isEmpty();
    }

    @Test
    void mixedModules_eachWithDuplicates_reportsEachDuplicate() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
            row("INC",  "inc_is_sinp_count_l3m_mtd"),
            row("INA",  "issue_rating_high_count_per_50_fte"),
            row("INC",  "inc_is_sinp_count_l3m_mtd"),   // duplicate in INC
            row("INA",  "issue_rating_high_count_per_50_fte") // duplicate in INA
        ));
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getRow()).isEqualTo(3);
        assertThat(errors.get(1).getRow()).isEqualTo(4);
    }

    @Test
    void unknownModule_noAllowlistCheck_noError() {
        // TPRM has no allow-list; any feature_name is accepted
        assertThat(validator.validate(List.of(row("TPRM", "any_feature")))).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private FeatureScoreBandCsvRow row(String module, String featureName) {
        return FeatureScoreBandCsvRow.builder()
            .module(module).featureBin(1).featureName(featureName)
            .rangeLow(BigDecimal.ZERO).rangeHigh(BigDecimal.ONE).score(10)
            .build();
    }
}
