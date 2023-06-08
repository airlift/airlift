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
package io.airlift.testing;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import javax.annotation.concurrent.GuardedBy;

import java.lang.annotation.Annotation;
import java.util.Set;

import static java.lang.String.format;

public final class ValidationAssertions
{
    @GuardedBy("VALIDATOR")
    private static final Validator VALIDATOR = Validation.byProvider(HibernateValidator.class)
            .configure()
            .ignoreXmlConfiguration()
            .messageInterpolator(new ParameterMessageInterpolator())
            .buildValidatorFactory()
            .getValidator();

    private ValidationAssertions() {}

    private static <T> Set<ConstraintViolation<T>> validate(T object)
    {
        synchronized (VALIDATOR) {
            return VALIDATOR.validate(object);
        }
    }

    public static void assertValidates(Object object)
    {
        assertValidates(object, null);
    }

    public static void assertValidates(Object object, String message)
    {
        if (!validate(object).isEmpty()) {
            throw new AssertionError(format("%sexpected:<%s> to pass validation", toMessageString(message), object));
        }
    }

    public static <T> void assertFailsValidation(T object, String field, String expectedErrorMessage, Class<? extends Annotation> annotation, String message)
    {
        Set<ConstraintViolation<T>> violations = validate(object);

        for (ConstraintViolation<T> violation : violations) {
            if (annotation.isInstance(violation.getConstraintDescriptor().getAnnotation()) &&
                    violation.getPropertyPath().toString().equals(field)) {
                if (!violation.getMessage().equals(expectedErrorMessage)) {
                    throw new AssertionError(format(
                            "%sexpected %s.%s for <%s> to fail validation for %s with message '%s', but message was '%s'",
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

        throw new AssertionError(format(
                "%sexpected %s.%s for <%s> to fail validation for %s with message '%s'",
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
