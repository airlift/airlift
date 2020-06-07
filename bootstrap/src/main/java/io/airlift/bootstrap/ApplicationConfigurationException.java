/*
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
package io.airlift.bootstrap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.internal.Messages;
import com.google.inject.spi.Message;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public final class ApplicationConfigurationException
        extends RuntimeException
{
    private final Set<Message> errors;
    private final Set<Message> warnings;
    private final List<Message> allMessages;

    public ApplicationConfigurationException(Collection<Message> errors, Collection<Message> warnings)
    {
        this.errors = ImmutableSet.copyOf(requireNonNull(errors, "errors is null"));
        checkArgument(!errors.isEmpty(), "no errors present");
        this.warnings = ImmutableSet.copyOf(requireNonNull(warnings, "warnings is null"));

        ImmutableList.Builder<Message> allMessages = ImmutableList.builder();
        for (Message error : errors) {
            allMessages.add(new Message(error.getSources(), "Error: " + error.getMessage(), error.getCause()));
        }
        for (Message warning : warnings) {
            allMessages.add(new Message(warning.getSources(), "Warning: " + warning.getMessage(), warning.getCause()));
        }
        this.allMessages = allMessages.build();
        initCause(Messages.getOnlyCause(this.allMessages));
    }

    public Set<Message> getErrors()
    {
        return errors;
    }

    public Set<Message> getWarnings()
    {
        return warnings;
    }

    @Override
    public String getMessage()
    {
        return Messages.formatMessages("Configuration errors", allMessages);
    }
}
