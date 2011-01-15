package com.proofpoint.configuration;

import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList;
import com.google.inject.ConfigurationException;
import com.google.inject.spi.Message;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import java.util.List;

class Problems
{
    private final List<Message> errors = Lists.newArrayList();
    private final List<Message> warnings = Lists.newArrayList();

    public void throwIfHasErrors() throws ConfigurationException
    {
        if (!errors.isEmpty()) {
            throw getException();
        }
    }

    public List<Message> getErrors()
    {
        return ImmutableList.copyOf(errors);
    }

    public void addError(String format, Object... params)
    {
        errors.add(new Message("Error: " + format(format, params)));
    }

    public void addError(Throwable e, String format, Object... params)
    {
        errors.add(new Message(emptyList(), "Error: " + format(format, params), e));
    }

    public List<Message> getWarnings()
    {
        return ImmutableList.copyOf(warnings);
    }

    public void addWarning(String format, Object... params)
    {
        warnings.add(new Message("Warning: " + format(format, params)));
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

    public static ConfigurationException exceptionFor(String format, Object... params)
    {
        Problems problems = new Problems();
        problems.addError(format, params);
        return problems.getException();
    }

    public static ConfigurationException exceptionFor(Throwable e, String format, Object... params)
    {
        Problems problems = new Problems();
        problems.addError(e, format, params);
        return problems.getException();
    }
}
