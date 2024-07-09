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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestMonitor
        implements Problems.Monitor
{
    private final Set<Message> errors = new LinkedHashSet<>();
    private final Set<Message> warnings = new LinkedHashSet<>();

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

    public Collection<Message> getErrors()
    {
        return errors;
    }

    public Collection<Message> getWarnings()
    {
        return warnings;
    }

    public void assertNumberOfErrors(int expected)
    {
        assertThat(errors.size()).as(String.format("Number of errors is incorrect, actual errors: %s", errorsString())).isEqualTo(expected);
    }

    public void assertNumberOfWarnings(int expected)
    {
        assertThat(warnings.size()).as(String.format("Number of warnings is incorrect, actual warnings: %s", warningsString())).isEqualTo(expected);
    }

    public void assertMatchingWarningRecorded(String... parts)
    {
        for (Message warning : warnings) {
            boolean matched = true;
            for (String part : parts) {
                if (!warning.getMessage().contains(part)) {
                    matched = false;
                    break;
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
                    break;
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

    private static String messageListAsString(Collection<Message> list)
    {
        StringBuilder builder = new StringBuilder();
        for (Message message : list) {
            builder.append(message.getMessage()).append(", ");
        }
        return builder.toString();
    }
}
