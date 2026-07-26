package com.dbs.mot.grc.validation;

import com.dbs.mot.grc.enums.PersistableEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.List;

/**
 * Validator for {@link PersistableEnumValue}: accepts {@code null}/blank, or a value matching one
 * of the target enum's {@code dbValue}s. On failure the message lists the allowed values.
 */
public class PersistableEnumValueValidator implements ConstraintValidator<PersistableEnumValue, String> {

    private List<String> allowedValues;

    @Override
    public void initialize(PersistableEnumValue annotation) {
        this.allowedValues = Arrays.stream(annotation.enumClass().getEnumConstants())
                .map(PersistableEnum::getDbValue)
                .toList();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank() || allowedValues.contains(value)) {
            return true;
        }
        // Replace the default message with one that lists the allowed values.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                "must be one of " + allowedValues).addConstraintViolation();
        return false;
    }
}
