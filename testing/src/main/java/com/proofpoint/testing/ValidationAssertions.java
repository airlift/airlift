package com.proofpoint.testing;

import com.google.common.annotations.Beta;
import org.apache.bval.jsr303.ApacheValidationProvider;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.lang.annotation.Annotation;
import java.util.Set;

import static java.lang.String.format;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Beta
public class ValidationAssertions
{
    private static final Validator VALIDATOR = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    public static void assertValidates(Object object)
    {
        assertValidates(object, null);
    }

    public static void assertValidates(Object object, String message)
    {
        assertTrue(VALIDATOR.validate(object).isEmpty(),
                   format("%sexpected:<%s> to pass validation", toMessageString(message), object));
    }

    public static <T> void assertFailsValidation(T object, String field, String expectedErrorMessage, Class<? extends Annotation> annotation, String message)
    {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object);

        for (ConstraintViolation<T> violation : violations) {
            if (annotation.isInstance(violation.getConstraintDescriptor().getAnnotation()) &&
                    violation.getPropertyPath().toString().equals(field)) {

                if (!violation.getMessage().equals(expectedErrorMessage)) {
                    fail(format("%sexpected %s.%s for <%s> to fail validation for %s with message '%s', but message was '%s'",
                                toMessageString(message),
                                object.getClass().getName(),
                                field,
                                message,
                                annotation.getName(),
                                expectedErrorMessage,
                                violation.getMessage()));

                }
                return;
            }
        }

        fail(format("%sexpected %s.%s for <%s> to fail validation for %s with message '%s'",
                    toMessageString(message),
                    object.getClass().getName(),
                    field,
                    object,
                    annotation.getName(),
                    expectedErrorMessage));
    }

    public static <T> void assertFailsValidation(T object, String field, String expectedErrorMessage, Class<? extends Annotation> annotation)
    {
        assertFailsValidation(object, field, expectedErrorMessage, annotation, null);
    }

    private static String toMessageString(String message)
    {
        return message == null ? "" : message + " ";
    }
}
