package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.common.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.BizUnitCsvRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BizUnitCsvRowValidatorTest {

    private final BizUnitCsvRowValidator validator = new BizUnitCsvRowValidator();

    @Test
    void validate_noErrors_whenAllRowsAreValid() {
        List<BizUnitCsvRow> rows = List.of(
                row(1, "ALL",  1, null,   null,  null),
                row(2, "Tech", 2, "Tech", null,  null),
                row(4, "DTI",  3, "Tech", "DTI", null),
                row(5, "BCM",  4, "Tech", "DTI", "BCM")
        );
        assertThat(validator.validate(rows)).isEmpty();
    }

    @Test
    void validate_detectsDuplicateBuNum() {
        List<BizUnitCsvRow> rows = List.of(
                row(1, "ALL",  1, null, null, null),
                row(1, "Tech", 2, null, null, null)
        );
        List<ValidationErrorDetail> errors = validator.validate(rows);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getField()).isEqualTo("BU_NUM");
        assertThat(errors.get(0).getRow()).isEqualTo(2);
    }

    @Test
    void validate_allowsNullOptionalFields() {
        assertThat(validator.validate(List.of(row(1, "ALL", 1, null, null, null)))).isEmpty();
    }

    @Test
    void validate_commaInBuNm_isNotRejected() {
        // The former "no comma" check was removed — BU_FULL_PATH and friends may legitimately
        // contain literal commas (e.g. inside "&"-joined names).
        assertThat(validator.validate(List.of(row(1, "Tech,Ops", 1, null, null, null)))).isEmpty();
    }

    @Test
    void validate_invalidOrlBuNmL2Reference_isNotRejected() {
        // The hierarchy reference check was removed — ORL_BU_NM_L2 no longer needs to resolve
        // to a BU_NM present in the same upload batch.
        List<BizUnitCsvRow> rows = List.of(
                row(2, "Tech", 2, "Tech",    null, null),
                row(4, "DTI",  3, "INVALID", "DTI", null)
        );
        assertThat(validator.validate(rows)).isEmpty();
    }

    @Test
    void validate_commaInBuFullPath_isNotRejected() {
        BizUnitCsvRow r = BizUnitCsvRow.builder()
                .buNum(1).buNm("ALL").lvlOfHier(1)
                .orlBuNmL2(null).orlBuNmL3(null).orlBuNmL4(null)
                .buFullPath("Tech,Ops/DTI").build();
        assertThat(validator.validate(List.of(r))).isEmpty();
    }

    private BizUnitCsvRow row(int buNum, String buNm, int lvl, String l2, String l3, String l4) {
        return BizUnitCsvRow.builder()
                .buNum(buNum).buNm(buNm).lvlOfHier(lvl)
                .orlBuNmL2(l2).orlBuNmL3(l3).orlBuNmL4(l4).build();
    }
}
