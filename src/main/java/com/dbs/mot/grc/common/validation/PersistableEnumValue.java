package com.dbs.mot.grc.common.validation;

import com.dbs.mot.grc.common.enums.PersistableEnum;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Validates that a String is either {@code null}/blank or a valid {@code dbValue} of the given
 * {@link PersistableEnum} type. Reusable across DTOs that accept an enum column value as text.
 */
@Documented
@Constraint(validatedBy = PersistableEnumValueValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface PersistableEnumValue {

    /** The persistable-enum type whose {@code dbValue}s are accepted. */
    Class<? extends PersistableEnum> enumClass();

    String message() default "must be a valid value";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
