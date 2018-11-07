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

import com.google.common.base.Joiner;
import com.google.inject.spi.Message;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

class TestMonitor
        implements Problems.Monitor
{
    private List<Message> errors = new ArrayList<>();
    private List<Message> warnings = new ArrayList<>();

    @Override
    public void onError(Message error)
    {
        errors.add(error);
    }

    @Override
    public void onWarning(Message warning)
    {
        warnings.add(warning);
    }

    public void assertNumberOfErrors(int expected)
    {
        assertEquals(errors.size(), expected, String.format("Number of errors is incorrect, actual errors: %s", errorsString()));
    }

    public void assertNumberOfWarnings(int expected)
    {
        assertEquals(warnings.size(), expected, String.format("Number of warnings is incorrect, actual warnings: %s", warningsString()));
    }

    public void assertMatchingWarningRecorded(String... parts)
    {
        for (Message warning : warnings) {
            boolean matched = true;
            for (String part : parts) {
                if (!warning.getMessage().contains(part)) {
                    matched = false;
                }
            }
            if (matched) {
                return;
            }
        }

        fail(String.format("Expected message (%s) not found in monitor warning list. Warnings: %s", Joiner.on(", ").join(parts), warningsString()));
    }

    public void assertMatchingErrorRecorded(String... parts)
    {
        for (Message error : errors) {
            boolean matched = true;
            for (String part : parts) {
                if (!error.getMessage().contains(part)) {
                    matched = false;
                }
            }
            if (matched) {
                return;
            }
        }

        fail(String.format("Expected message (%s) not found in monitor error list. Errors: %s", Joiner.on(", ").join(parts), errorsString()));
    }

    private String errorsString()
    {
        return messageListAsString(errors);
    }

    private String warningsString()
    {
        return messageListAsString(warnings);
    }

    private String messageListAsString(List<Message> list)
    {
        StringBuilder builder = new StringBuilder();
        for (Message message : list) {
            builder.append(message.getMessage()).append(", ");
        }
        return builder.toString();
    }
}
