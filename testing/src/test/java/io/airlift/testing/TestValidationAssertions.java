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

import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static io.airlift.testing.ValidationAssertions.assertFailsValidation;
import static io.airlift.testing.ValidationAssertions.assertValidates;
import static org.assertj.core.api.Assertions.assertThat;

public class TestValidationAssertions
{
    private static final String MESSAGE = "@message@";
    private static final Bean VALID_OBJECT = new Bean(new Object());
    private static final Bean INVALID_OBJECT = new Bean(null);

    @Test
    public void testAssertValidates()
    {
        assertValidates(VALID_OBJECT);
        assertValidates(VALID_OBJECT, MESSAGE);
    }

    @Test
    public void testAssertValidatesThrowsWithInvalidObject()
    {
        boolean ok = false;
        try {
            ValidationAssertions.assertValidates(INVALID_OBJECT);
        }
        catch (AssertionError e) {
            ok = true;
            verifyExceptionMessage(e, null, INVALID_OBJECT, null, null);
        }
        assertThat(ok).as("Expected AssertionError").isTrue();
    }

    @Test
    public void testAssertValidatesThrowsWithInvalidObjectWithMessage()
    {
        boolean ok = false;
        try {
            assertValidates(INVALID_OBJECT, MESSAGE);
        }
        catch (AssertionError e) {
            ok = true;
            // success
            verifyExceptionMessage(e, MESSAGE, INVALID_OBJECT, null, null);
        }
        assertThat(ok).as("Expected AssertionError").isTrue();
    }

    @Test
    public void testTheAssertFailsValidationMethodSucceedsWithInvalidObject()
    {
        assertFailsValidation(INVALID_OBJECT, "value", "must not be null", NotNull.class);
    }

    @Test
    public void testTheAssertFailsValidationWithMessageMethodSucceedsWithInvalidObject()
    {
        assertFailsValidation(INVALID_OBJECT, "value", "must not be null", NotNull.class, MESSAGE);
    }

    @Test
    public void testTheAssertFailsValidationMethodThrowsWithValidObject()
    {
        boolean ok = false;
        try {
            assertFailsValidation(VALID_OBJECT, "value", "must not be null", NotNull.class);
        }
        catch (AssertionError e) {
            ok = true;
            verifyExceptionMessage(e, null, VALID_OBJECT, "value", NotNull.class);
        }

        assertThat(ok).as("Expected AssertionError").isTrue();
    }

    @Test
    public void testTheAssertFailsValidationWithMessageMethodThrowsWithValidObject()
    {
        boolean ok = false;
        try {
            assertFailsValidation(VALID_OBJECT, "value", "must not be null", NotNull.class, MESSAGE);
        }
        catch (AssertionError e) {
            ok = true;
            // success
            verifyExceptionMessage(e, MESSAGE, VALID_OBJECT, "value", NotNull.class);
        }
        assertThat(ok).as("Expected AssertionError").isTrue();
    }

    private void verifyExceptionMessage(AssertionError e, String message, Object value, String property, Class<? extends Annotation> annotation)
    {
        assertThat(e).isNotNull();
        String actualMessage = e.getMessage();
        assertThat(actualMessage).isNotNull();
        if (message != null) {
            assertThat(actualMessage.startsWith(message + " ")).isTrue();
        }
        else {
            assertThat(actualMessage).doesNotStartWith(" ");
        }

        assertThat(actualMessage).contains("<" + value + ">");

        if (annotation != null) {
            assertThat(actualMessage).contains(annotation.getName());
        }

        if (property != null) {
            assertThat(actualMessage).contains(property);
        }
    }

    public static class Bean
    {
        private Object value;

        private Bean(Object value)
        {
            this.value = value;
        }

        @NotNull
        public Object getValue()
        {
            return value;
        }
    }
}
