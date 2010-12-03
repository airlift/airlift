package com.proofpoint.configuration;

import com.google.common.collect.Lists;
import com.google.inject.ConfigurationException;
import com.google.inject.spi.Message;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import java.util.List;

class Errors<T>
{
    private final List<Message> errors = Lists.newArrayList();

    public void throwIfHasErrors()
    {
        if (!errors.isEmpty()) {
            throw new ConfigurationException(errors);
        }
    }

    public void add(String format, Object... params)
    {
        errors.add(new Message(format(format, params)));
    }

    public void add(Throwable e, String format, Object... params)
    {
        errors.add(new Message(emptyList(), format(format, params), e));
    }
}
