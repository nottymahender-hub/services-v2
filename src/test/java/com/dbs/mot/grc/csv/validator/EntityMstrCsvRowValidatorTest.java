package com.dbs.mot.grc.csv.validator;

import com.dbs.mot.grc.dto.ValidationErrorDetail;
import com.dbs.mot.grc.dto.EntityMstrCsvRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMstrCsvRowValidatorTest {

    private final EntityMstrCsvRowValidator validator = new EntityMstrCsvRowValidator();

    @Test void valid_noErrors() {
        assertThat(validator.validate(List.of(row(1,"Alpha","SG",null), row(2,"Beta","HK","IC-01")))).isEmpty();
    }

    @Test void duplicateEntityNum_addsError() {
        List<ValidationErrorDetail> e = validator.validate(List.of(row(1,"Alpha","SG",null), row(1,"Beta","HK",null)));
        assertThat(e).hasSize(1);
        assertThat(e.get(0).getField()).isEqualTo("ENTITY_NUM");
    }

    @Test void duplicateEntityNm_addsError() {
        List<ValidationErrorDetail> e = validator.validate(List.of(row(1,"Alpha","SG",null), row(2,"Alpha","HK",null)));
        assertThat(e).hasSize(1);
        assertThat(e.get(0).getField()).isEqualTo("ENTITY_NM");
    }

    @Test void duplicateEntityNm_caseInsensitive() {
        assertThat(validator.validate(List.of(row(1,"Alpha","SG",null), row(2,"ALPHA","HK",null)))).hasSize(1);
    }

    @Test void forbiddenLocationIc_addsError() {
        List<ValidationErrorDetail> e = validator.validate(List.of(row(1,"Alpha","SG","IC Rollup")));
        assertThat(e).hasSize(1);
        assertThat(e.get(0).getField()).isEqualTo("orl_location_ic");
    }

    @Test void nullLocationIc_allowed() {
        assertThat(validator.validate(List.of(row(1,"Alpha","SG",null)))).isEmpty();
    }

    private EntityMstrCsvRow row(int num, String nm, String loc, String locIc) {
        return EntityMstrCsvRow.builder().entityNum(num).entityNm(nm)
                .orlLocation(loc).orlLocationIc(locIc).build();
    }
}
