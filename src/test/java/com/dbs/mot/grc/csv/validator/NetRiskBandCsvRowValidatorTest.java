package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.NetRiskBandCsvRow;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NetRiskBandCsvRowValidatorTest {

    private final NetRiskBandCsvRowValidator validator = new NetRiskBandCsvRowValidator();

    @Test void valid_noErrors() {
        assertThat(validator.validate(List.of(row("INC","Low"), row("INC","High")))).isEmpty();
    }

    @Test void duplicateComposite_addsError() {
        List<ValidationErrorDetail> e = validator.validate(List.of(row("INC","Low"), row("INC","Low")));
        assertThat(e).hasSize(1);
        assertThat(e.get(0).getRow()).isEqualTo(2);
    }

    @Test void differentModule_allowed() {
        assertThat(validator.validate(List.of(row("INC","Low"), row("INA","Low")))).isEmpty();
    }

    private NetRiskBandCsvRow row(String module, String rtng) {
        return NetRiskBandCsvRow.builder().module(module)
                .netRiskRtng(rtng).rangeLow(BigDecimal.ZERO).rangeHigh(BigDecimal.ONE).build();
    }
}
