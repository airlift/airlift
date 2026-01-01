package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.OptionalDouble;

import static com.google.common.base.MoreObjects.firstNonNull;

public record Annotations(List<Role> audience, OptionalDouble priority)
{
    public static final Annotations EMPTY = new Annotations(ImmutableList.of(), OptionalDouble.empty());

    public Annotations
    {
        audience = ImmutableList.copyOf(audience);
        priority = firstNonNull(priority, OptionalDouble.empty());
    }
}
