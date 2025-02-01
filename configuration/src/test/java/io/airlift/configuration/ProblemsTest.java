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
package io.airlift.configuration;

import com.google.inject.ConfigurationException;
import com.google.inject.spi.Message;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ProblemsTest
{
    @Test
    public void testOneError()
    {
        Problems problems = new Problems();
        problems.addError("message %d", 1);

        List<Message> errors = problems.getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).isEqualTo("message 1");

        assertThat(problems.getWarnings().size()).as("Found unexpected warnings in problem object").isEqualTo(0);

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertThat(e.getMessage()).contains("message 1");
        }
    }

    @Test
    public void testTwoErrors()
    {
        Problems problems = new Problems();
        problems.addError("message %d", 1);
        problems.addError("message %d", 2);

        List<Message> errors = problems.getErrors();
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getMessage()).isEqualTo("message 1");
        assertThat(errors.get(1).getMessage()).isEqualTo("message 2");

        assertThat(problems.getWarnings().size()).as("Found unexpected warnings in problem object").isEqualTo(0);

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertThat(e.getMessage()).contains("message 1", "message 2");
        }
    }

    @Test
    @SuppressWarnings("FormatStringAnnotation")
    public void testFormatError()
    {
        Problems problems = new Problems();
        problems.addError("message %d", "NaN");

        List<Message> errors = problems.getErrors();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("message %d", "NaN", "IllegalFormatConversionException");

        assertThat(problems.getWarnings().size()).as("Found unexpected warnings in problem object").isEqualTo(0);

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertThat(e.getMessage()).contains("message %d [NaN]");
        }
    }

    @Test
    public void testOneWarning()
    {
        Problems problems = new Problems();
        problems.addWarning("message %d", 1);

        assertThat(problems.getErrors().size()).as("Found unexpected errors in problem object").isEqualTo(0);

        List<Message> warnings = problems.getWarnings();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getMessage()).isEqualTo("message 1");

        try {
            problems.throwIfHasErrors();
        }
        catch (ConfigurationException cause) {
            org.assertj.core.api.Assertions.fail("Didn't expect problems object to throw", cause);
        }
    }

    @Test
    public void testTwoWarnings()
    {
        Problems problems = new Problems();
        problems.addWarning("message %d", 1);
        problems.addWarning("message %d", 2);

        assertThat(problems.getErrors().size()).as("Found unexpected errors in problem object").isEqualTo(0);

        List<Message> warnings = problems.getWarnings();
        assertThat(warnings).hasSize(2);
        assertThat(warnings.get(0).getMessage()).isEqualTo("message 1");
        assertThat(warnings.get(1).getMessage()).isEqualTo("message 2");

        try {
            problems.throwIfHasErrors();
        }
        catch (ConfigurationException cause) {
            org.assertj.core.api.Assertions.fail("Didn't expect problems object to throw", cause);
        }
    }

    @Test
    @SuppressWarnings("FormatStringAnnotation")
    public void testFormatWarning()
    {
        Problems problems = new Problems();
        problems.addWarning("message %d", "NaN");

        assertThat(problems.getErrors().size()).as("Found unexpected errors in problem object").isEqualTo(0);

        List<Message> warnings = problems.getWarnings();
        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getMessage()).contains("message %d", "NaN", "IllegalFormatConversionException");

        try {
            problems.throwIfHasErrors();
        }
        catch (ConfigurationException cause) {
            org.assertj.core.api.Assertions.fail("Didn't expect problems object to throw", cause);
        }
    }

    @Test
    public void testErrorsAndWarnings()
    {
        Problems problems = new Problems();
        problems.addError("message e%d", 1);
        problems.addError("message e%d", 2);

        problems.addWarning("message w%d", 1);
        problems.addWarning("message w%d", 2);
        problems.addWarning("message w%d", 3);

        List<Message> errors = problems.getErrors();
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getMessage()).isEqualTo("message e1");
        assertThat(errors.get(1).getMessage()).isEqualTo("message e2");

        List<Message> warnings = problems.getWarnings();
        assertThat(warnings).hasSize(3);
        assertThat(warnings.get(0).getMessage()).isEqualTo("message w1");
        assertThat(warnings.get(1).getMessage()).isEqualTo("message w2");
        assertThat(warnings.get(2).getMessage()).isEqualTo("message w3");

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertThat(e.getMessage()).contains("message e1", "message e2", "message w1", "message w2", "message w3");
        }
    }
}
