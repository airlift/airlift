package com.proofpoint.configuration;

import com.google.common.collect.Lists;

import java.util.List;

class Errors<T>
{
    private T object;
    private final List<String> errors = Lists.newArrayList();

    public void setPartial(T object)
    {
        this.object = object;
    }

    public void throwIfHasErrors()
    {
        if (!errors.isEmpty()) {
            throw new ConfigurationException(object, errors);
        }
    }

    public void add(String format, Object... params)
    {
        errors.add(String.format(format, params));
    }
}
