package com.proofpoint.configuration;

import com.google.common.collect.Lists;
import com.google.common.collect.ImmutableList;
import com.google.inject.ConfigurationException;
import com.google.inject.spi.Message;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import java.util.List;
import java.util.Collection;

class Errors
{
    private final List<Message> errors = Lists.newArrayList();

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

    public void add(String format, Object... params)
    {
        errors.add(new Message(format(format, params)));
    }

    public void add(Throwable e, String format, Object... params)
    {
        errors.add(new Message(emptyList(), format(format, params), e));
    }

    private ConfigurationException getException()
    {
        return new ConfigurationException(errors);
    }

    public static ConfigurationException exceptionFor(String format, Object... params)
    {
        Errors errors = new Errors();
        errors.add(format, params);
        return errors.getException();
    }

    public static ConfigurationException exceptionFor(Throwable e, String format, Object... params)
    {
        Errors errors = new Errors();
        errors.add(e, format, params);
        return errors.getException();
    }
}
