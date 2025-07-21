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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import com.google.inject.ConfigurationException;
import com.google.inject.spi.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Collections.emptyList;

class Problems
{
    private final List<Message> errors = new ArrayList<>();
    private final List<Message> warnings = new ArrayList<>();

    public void throwIfHasErrors()
            throws ConfigurationException
    {
        if (!errors.isEmpty()) {
            throw getException();
        }
    }

    public List<Message> getErrors()
    {
        return ImmutableList.copyOf(errors);
    }

    void record(Problems problems)
    {
        checkArgument(problems != this, "Can not add problems to itself");
        errors.addAll(problems.errors);
        warnings.addAll(problems.warnings);
    }

    @FormatMethod
    public void addError(@FormatString String format, Object... params)
    {
        Message message = new Message(format(format, params));
        errors.add(message);
    }

    @FormatMethod
    public void addError(Throwable e, @FormatString String format, Object... params)
    {
        Message message = new Message(emptyList(), format(format, params), e);
        errors.add(message);
    }

    public void addError(Throwable e)
    {
        Message message = new Message(emptyList(), e.getMessage(), e.getCause());
        errors.add(message);
    }

    public List<Message> getWarnings()
    {
        return ImmutableList.copyOf(warnings);
    }

    @FormatMethod
    public void addWarning(@FormatString String format, Object... params)
    {
        Message message = new Message(format(format, params));
        warnings.add(message);
    }

    public void addWarning(String text)
    {
        Message message = new Message(text);
        warnings.add(message);
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        for (Message error : errors) {
            builder.append(error.getMessage()).append('\n');
        }
        for (Message warning : warnings) {
            builder.append(warning.getMessage()).append('\n');
        }
        return builder.toString();
    }

    private ConfigurationException getException()
    {
        ImmutableList<Message> messages
                = new ImmutableList.Builder<Message>()
                .addAll(errors)
                .addAll(warnings)
                .build();

        return new ConfigurationException(messages);
    }

    @FormatMethod
    public static ConfigurationException exceptionFor(@FormatString String format, Object... params)
    {
        Problems problems = new Problems();
        problems.addError(format, params);
        return problems.getException();
    }

    @FormatMethod
    public static ConfigurationException exceptionFor(Throwable e, @FormatString String format, Object... params)
    {
        Problems problems = new Problems();
        problems.addError(e, format, params);
        return problems.getException();
    }

    @FormatMethod
    private static String format(@FormatString String format, Object... params)
    {
        if (format == null || params.length == 0) {
            return format;
        }

        try {
            return String.format(format, params);
        }
        catch (IllegalFormatException e) {
            return String.format("%s %s <%s>", format, Arrays.toString(params), e);
        }
    }
}
