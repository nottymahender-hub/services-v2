package com.dbs.mot.grc.common.enums;

import com.dbs.mot.grc.common.config.PersistableEnumToStringConverter;
import com.dbs.mot.grc.common.config.StringToPersistableEnumConverterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link PersistableEnum} machinery: db-value resolution, the net-risk
 * display form, and the Spring Data JDBC read/write converters.
 */
class PersistableEnumTest {

    @Test
    void netRiskRating_dbAndDisplayValues() {
        assertThat(NetRiskRating.MED_LOW.getDbValue()).isEqualTo("Med Low");
        assertThat(NetRiskRating.MED_LOW.getDisplayValue()).isEqualTo("Medium-Low Risk");
        assertThat(NetRiskRating.fromDbValue("High")).isEqualTo(NetRiskRating.HIGH);
        assertThat(NetRiskRating.display(NetRiskRating.HIGH)).isEqualTo("High Risk");
        assertThat(NetRiskRating.display(null)).isNull();
    }

    @Test
    void fromDbValue_blankReturnsNull_unknownThrows() {
        assertThat(PersistableEnums.fromDbValue(Module.class, null)).isNull();
        assertThat(PersistableEnums.fromDbValue(Module.class, "  ")).isNull();
        assertThatThrownBy(() -> PersistableEnums.fromDbValue(Module.class, "NOPE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Module");
    }

    @Test
    void dbValueHelper_isNullSafe() {
        assertThat(PersistableEnum.dbValue(null)).isNull();
        assertThat(PersistableEnum.dbValue(AssmtStatus.PARTIAL_LOCKED)).isEqualTo("Partial locked");
    }

    @Test
    void writingConverter_returnsDbValue() {
        assertThat(new PersistableEnumToStringConverter().convert(LevelCategory.GRP_L2)).isEqualTo("grp_l2");
    }

    @Test
    void readingConverterFactory_resolvesByDbValue() {
        Converter<String, NetRiskRating> converter =
                new StringToPersistableEnumConverterFactory().getConverter(NetRiskRating.class);

        assertThat(converter.convert("Med High")).isEqualTo(NetRiskRating.MED_HIGH);
        assertThat(converter.convert("")).isNull();
        assertThatThrownBy(() -> converter.convert("bogus"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
