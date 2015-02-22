/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.testing;

import com.google.common.annotations.Beta;
import org.apache.bval.jsr303.ApacheValidationProvider;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.lang.annotation.Annotation;
import java.util.Set;

import static java.lang.String.format;
import static org.testng.Assert.fail;

@Beta
public class ValidationAssertions
{
    private static final Validator VALIDATOR = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    private ValidationAssertions()
    {
    }

    public static <T> T assertValidates(T object)
    {
        return assertValidates(object, null);
    }

    public static <T> T assertValidates(T object, String message)
    {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object);
        if (violations.isEmpty()) {
            return object;
        }
        StringBuilder builder = new StringBuilder(toMessageString(message));
        builder.append("expected:<").append(object).append("> to pass validation:");
        appendViolations(builder, violations);
        fail(builder.toString());
        return object;
    }

    private static <T> void appendViolations(StringBuilder builder, Set<ConstraintViolation<T>> violations)
    {
        for (ConstraintViolation<T> violation : violations) {
            builder.append("\n\t")
                    .append(violation.getPropertyPath().toString())
                    .append(" failed validation for ")
                    .append(violation.getConstraintDescriptor().getAnnotation().annotationType().getName())
                    .append(" with message '")
                    .append(violation.getMessage())
                    .append("'");
        }
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

        StringBuilder builder = new StringBuilder(format("%sexpected %s.%s for <%s> to fail validation for %s with message '%s'",
                toMessageString(message),
                object.getClass().getName(),
                field,
                object,
                annotation.getName(),
                expectedErrorMessage));
        if (violations.isEmpty()) {
            builder.append(" but it passed");
        } else {
            builder.append(" but it failed for other fields:");
            appendViolations(builder, violations);
        }
        fail(builder.toString());
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
