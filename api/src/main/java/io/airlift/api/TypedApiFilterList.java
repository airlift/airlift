package io.airlift.api;

import com.google.common.collect.ImmutableList;

import java.util.List;

public record TypedApiFilterList<T>(List<T> values)
{
    public TypedApiFilterList
    {
        values = ImmutableList.copyOf(values);
    }
}
