package com.proofpoint.units;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target( { METHOD, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Documented
@Constraint(validatedBy = MinDurationValidator.class)
public @interface MinDuration
{
    String value();
    String message() default "{com.proofpoint.units.MinDuration.message}";
    Class<?>[] groups() default { };
    Class<? extends Payload>[] payload() default { };
}

