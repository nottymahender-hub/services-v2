package com.dbs.mot.grc.common.validation;

import com.dbs.mot.grc.common.enums.NetRiskRating;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PersistableEnumValueValidator}.
 */
class PersistableEnumValueValidatorTest {

    private final PersistableEnumValueValidator validator = new PersistableEnumValueValidator();

    private PersistableEnumValueValidator forEnum() {
        PersistableEnumValue annotation = Mockito.mock(PersistableEnumValue.class);
        Mockito.doReturn(NetRiskRating.class).when(annotation).enumClass();
        validator.initialize(annotation);
        return validator;
    }

    @Test
    void nullOrBlank_isValid() {
        PersistableEnumValueValidator v = forEnum();
        assertThat(v.isValid(null, null)).isTrue();
        assertThat(v.isValid("  ", null)).isTrue();
    }

    @Test
    void validDbValue_isValid() {
        assertThat(forEnum().isValid("Med High", null)).isTrue();
    }

    @Test
    void unknownValue_isInvalid_withCustomMessage() {
        PersistableEnumValueValidator v = forEnum();
        ConstraintValidatorContext ctx = Mockito.mock(ConstraintValidatorContext.class);
        var builder = Mockito.mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        Mockito.when(ctx.buildConstraintViolationWithTemplate(Mockito.anyString())).thenReturn(builder);

        assertThat(v.isValid("bogus", ctx)).isFalse();
        Mockito.verify(ctx).disableDefaultConstraintViolation();
        Mockito.verify(ctx).buildConstraintViolationWithTemplate(Mockito.contains("must be one of"));
    }
}
