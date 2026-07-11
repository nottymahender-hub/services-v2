package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.common.csv.CsvRowValidator;
import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.FeatureScoreBandCsvRow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class FeatureScoreBandCsvRowValidator implements CsvRowValidator<FeatureScoreBandCsvRow> {

    static final Map<String, Set<String>> ALLOWED_FEATURE_NAMES = Map.of(
        "INC", Set.of(
            "inc_is_sinp_count_l3m_mtd",
            "inc_is_mi_count_l3m_mtd",
            "inc_is_gorc_count_l3m_mtd_per_50_fte",
            "inc_is_min_reportable_count_l3m_mtd_per_50_fte",
            "inc_avg_time_to_detect_l11m_mtd"
        ),
        "INA", Set.of(
            "issue_rating_high_count_per_50_fte",
            "issue_rating_medium_count_per_50_fte",
            "issue_type_regulatory_count_per_50_fte",
            "issue_type_audit_count_per_50_fte",
            "issue_type_others_count_per_50_fte",
            "issue_open_count_per_50_fte",
            "issue_closed_count_per_50_fte",
            "issue_repeated_count"
        ),
        "RCSA", Set.of(
            "rcsa_weighted_avg_risk",
            "rcsa_high_medhigh_risk_vs_medlow_low_risk_proportion"
        ),
        "KRI", Set.of(
            "kri_sustained_red_3m_or_quarterly_red_proportion",
            "kri_sustained_red_2m_proportion",
            "kri_sustained_red_amber_4m_or_quarterly_amber_proportion",
            "kri_amber_sustained_red_amber_3m_proportion"
        )
    );

    @Override
    public List<ValidationErrorDetail> validate(List<FeatureScoreBandCsvRow> rows) {
        List<ValidationErrorDetail> errors = new ArrayList<>();
        // tracks module|feature_name → first seen row number
        Map<String, Integer> seenModuleFeature = new HashMap<>();

        for (int i = 0; i < rows.size(); i++) {
            FeatureScoreBandCsvRow row = rows.get(i);
            int rowNum = i + 1;
            String module = row.getModule();
            String featureName = row.getFeatureName();

            if (module == null || featureName == null) {
                continue;
            }

            Set<String> allowed = ALLOWED_FEATURE_NAMES.get(module);
            if (allowed != null && !allowed.contains(featureName)) {
                errors.add(ValidationErrorDetail.builder()
                    .row(rowNum).field("feature_name")
                    .message("feature_name '" + featureName + "' is not an allowed value for module '"
                        + module + "'. Allowed values: " + allowed + ".")
                    .build());
                continue;
            }

            String key = module + "|" + featureName + "|" + row.getFeatureBin();
            if (seenModuleFeature.containsKey(key)) {
                errors.add(ValidationErrorDetail.builder()
                    .row(rowNum).field("feature_name")
                    .message("Duplicate combination (feature_name=" + featureName
                        + ", feature_bin=" + row.getFeatureBin() + ", module=" + module
                        + ") — already defined in row " + seenModuleFeature.get(key) + ".")
                    .build());
            } else {
                seenModuleFeature.put(key, rowNum);
            }
        }
        return errors;
    }
}
