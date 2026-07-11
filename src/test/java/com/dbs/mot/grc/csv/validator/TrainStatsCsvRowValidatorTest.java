package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.TrainStatsCsvRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrainStatsCsvRowValidatorTest {

    private final TrainStatsCsvRowValidator validator = new TrainStatsCsvRowValidator();

    @Test void valid_noErrors() {
        assertThat(validator.validate(List.of(row("L2","INC"), row("L3","INC"), row("L2","INA")))).isEmpty();
    }

    @Test void duplicateComposite_addsError() {
        List<ValidationErrorDetail> e = validator.validate(List.of(row("L2","INC"), row("L2","INC")));
        assertThat(e).hasSize(1);
        assertThat(e.get(0).getRow()).isEqualTo(2);
    }

    @Test void differentModule_allowed() {
        assertThat(validator.validate(List.of(row("L2","INC"), row("L2","INA")))).isEmpty();
    }

    private TrainStatsCsvRow row(String lvl, String module) {
        return TrainStatsCsvRow.builder().lvl(lvl).module(module)
                .trainMean(BigDecimal.ONE).trainVar(BigDecimal.ZERO).build();
    }
}
