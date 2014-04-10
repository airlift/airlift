package com.proofpoint.configuration;

public class MapClasses
{
    private final Class<?> key;
    private final Class<?> value;

    MapClasses(Class<?> key, Class<?> value)
    {
        this.key = key;
        this.value = value;
    }

    public Class<?> getKey()
    {
        return key;
    }

    public Class<?> getValue()
    {
        return value;
    }
}
