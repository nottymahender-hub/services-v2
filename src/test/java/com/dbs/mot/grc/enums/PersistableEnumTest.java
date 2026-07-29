package com.dbs.mot.grc.enums;

import com.dbs.mot.grc.config.PersistableEnumToStringConverter;
import com.dbs.mot.grc.config.StringToPersistableEnumConverterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link PersistableEnum} machinery: db-value resolution and the Spring Data
 * JDBC read/write converters.
 */
class PersistableEnumTest {

    @Test
    void netRiskRating_dbValues() {
        // Net risk rating is surfaced exactly as stored (no separate display label).
        assertThat(NetRiskRating.MED_LOW.getDbValue()).isEqualTo("Med Low");
        assertThat(NetRiskRating.fromDbValue("High")).isEqualTo(NetRiskRating.HIGH);
        assertThat(PersistableEnum.dbValue(NetRiskRating.HIGH)).isEqualTo("High");
        assertThat(PersistableEnum.dbValue((NetRiskRating) null)).isNull();
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
