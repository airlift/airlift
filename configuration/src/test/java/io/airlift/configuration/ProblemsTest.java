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
import org.testng.annotations.Test;

import java.util.List;

import static io.airlift.testing.Assertions.assertContains;
import static io.airlift.testing.Assertions.assertContainsAllOf;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class ProblemsTest
{
    @Test
    public void testOneError()
    {
        Problems problems = new Problems();
        problems.addError("message %d", 1);

        List<Message> errors = problems.getErrors();
        assertEquals(errors.size(), 1);
        assertEquals(errors.getFirst().getMessage(), "message 1");

        assertEquals(problems.getWarnings().size(), 0, "Found unexpected warnings in problem object");

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertContainsAllOf(e.getMessage(), "message 1");
        }
    }

    @Test
    public void testTwoErrors()
    {
        Problems problems = new Problems();
        problems.addError("message %d", 1);
        problems.addError("message %d", 2);

        List<Message> errors = problems.getErrors();
        assertEquals(errors.size(), 2);
        assertEquals(errors.getFirst().getMessage(), "message 1");
        assertEquals(errors.get(1).getMessage(), "message 2");

        assertEquals(problems.getWarnings().size(), 0, "Found unexpected warnings in problem object");

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertContainsAllOf(e.getMessage(), "message 1", "message 2");
        }
    }

    @Test
    public void testFormatError()
    {
        Problems problems = new Problems();
        problems.addError("message %d", "NaN");

        List<Message> errors = problems.getErrors();
        assertEquals(errors.size(), 1);
        assertContainsAllOf(errors.getFirst().getMessage(), "message %d", "NaN", "IllegalFormatConversionException");

        assertEquals(problems.getWarnings().size(), 0, "Found unexpected warnings in problem object");

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertContains(e.getMessage(), "message %d [NaN]");
        }
    }

    @Test
    public void testOneWarning()
    {
        Problems problems = new Problems();
        problems.addWarning("message %d", 1);

        assertEquals(problems.getErrors().size(), 0, "Found unexpected errors in problem object");

        List<Message> warnings = problems.getWarnings();
        assertEquals(warnings.size(), 1);
        assertEquals(warnings.getFirst().getMessage(), "message 1");

        try {
            problems.throwIfHasErrors();
        }
        catch (ConfigurationException cause) {
            fail("Didn't expect problems object to throw", cause);
        }
    }

    @Test
    public void testTwoWarnings()
    {
        Problems problems = new Problems();
        problems.addWarning("message %d", 1);
        problems.addWarning("message %d", 2);

        assertEquals(problems.getErrors().size(), 0, "Found unexpected errors in problem object");

        List<Message> warnings = problems.getWarnings();
        assertEquals(warnings.size(), 2);
        assertEquals(warnings.get(0).getMessage(), "message 1");
        assertEquals(warnings.get(1).getMessage(), "message 2");

        try {
            problems.throwIfHasErrors();
        }
        catch (ConfigurationException cause) {
            fail("Didn't expect problems object to throw", cause);
        }
    }

    @Test
    public void testFormatWarning()
    {
        Problems problems = new Problems();
        problems.addWarning("message %d", "NaN");

        assertEquals(problems.getErrors().size(), 0, "Found unexpected errors in problem object");

        List<Message> warnings = problems.getWarnings();
        assertEquals(warnings.size(), 1);
        assertContainsAllOf(warnings.getFirst().getMessage(), "message %d", "NaN", "IllegalFormatConversionException");

        try {
            problems.throwIfHasErrors();
        }
        catch (ConfigurationException cause) {
            fail("Didn't expect problems object to throw", cause);
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
        assertEquals(errors.size(), 2);
        assertEquals(errors.get(0).getMessage(), "message e1");
        assertEquals(errors.get(1).getMessage(), "message e2");

        List<Message> warnings = problems.getWarnings();
        assertEquals(warnings.size(), 3);
        assertEquals(warnings.get(0).getMessage(), "message w1");
        assertEquals(warnings.get(1).getMessage(), "message w2");
        assertEquals(warnings.get(2).getMessage(), "message w3");

        try {
            problems.throwIfHasErrors();
            fail("Expected exception from problems object");
        }
        catch (ConfigurationException e) {
            assertContainsAllOf(e.getMessage(), "message e1", "message e2", "message w1", "message w2", "message w3");
        }
    }

    private static class SimpleMonitor
            implements Problems.Monitor
    {
        private String result = "";

        public void onError(Message error)
        {
            result = result + "E-" + error.getMessage() + ", ";
        }

        public void onWarning(Message warning)
        {
            result = result + "W-" + warning.getMessage() + ", ";
        }

        public String getResult()
        {
            return result;
        }
    }

    @Test
    public void testMonitor()
    {
        SimpleMonitor monitor = new SimpleMonitor();
        Problems problems = new Problems(monitor);

        problems.addError("1");
        problems.addWarning("1");
        problems.addWarning("2");
        problems.addWarning("3");
        problems.addError("2");

        assertContains(monitor.getResult(), "E-1, W-1, W-2, W-3, E-2");
    }
}
