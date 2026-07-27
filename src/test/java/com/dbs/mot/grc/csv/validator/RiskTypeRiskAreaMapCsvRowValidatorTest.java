package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.dto.RiskTypeRiskAreaMapCsvRow;
import com.dbs.mot.grc.dto.ValidationErrorDetail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RiskTypeRiskAreaMapCsvRowValidator} — the two batch uniqueness rules.
 * The validator is a plain component with no dependencies, so it is exercised directly.
 */
class RiskTypeRiskAreaMapCsvRowValidatorTest {

    private final RiskTypeRiskAreaMapCsvRowValidator validator = new RiskTypeRiskAreaMapCsvRowValidator();

    @Test
    void allUnique_noErrors() {
        assertThat(validator.validate(List.of(
                row("Market Abuse", 89, "N", "CONDUCT"),
                row("Data Governance", 123, "Y", "DATA"),
                row("Data Management", 180, "Y", "DATA")   // same cluster, different pair → OK
        ))).isEmpty();
    }

    @Test
    void blankRiskCluster_ignoredByUniquenessRule() {
        // Two blank/null clusters are allowed (the column is optional); pairs still differ.
        assertThat(validator.validate(List.of(
                row("A", 1, "Y", null),
                row("B", 2, "Y", "")
        ))).isEmpty();
    }

    @Test
    void duplicatePair_reportsPairError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("OR", 101, "Y", "CL1"),
                row("OR", 101, "Y", "CL2")   // distinct clusters → only the pair rule fires
        ));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("RISK_AREA/RISK_TYPE_L4_NUM");
    }

    @Test
    void duplicateRiskClusterForSamePair_reportsClusterError() {
        List<ValidationErrorDetail> errors = validator.validate(List.of(
                row("OR", 101, "Y", "CL1"),
                row("OR", 101, "Y", "CL1")   // same pair + same cluster → both rules fire
        ));
        assertThat(errors).extracting(ValidationErrorDetail::getField)
                .contains("RISK_CLUSTER", "RISK_AREA/RISK_TYPE_L4_NUM");
        assertThat(errors).anySatisfy(e -> {
            if ("RISK_CLUSTER".equals(e.getField())) {
                assertThat(e.getMessage()).contains("CL1").contains("first seen at row 1");
            }
        });
    }

    private static RiskTypeRiskAreaMapCsvRow row(String riskArea, int num, String isOrFa, String cluster) {
        return RiskTypeRiskAreaMapCsvRow.builder()
                .riskArea(riskArea).riskTypeL4Num(num).riskTypeL4Nm(riskArea)
                .isOrFa(isOrFa).riskCluster(cluster).build();
    }
}
