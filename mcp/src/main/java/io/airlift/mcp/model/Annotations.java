package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record Annotations(List<Role> audience, Optional<Double> priority)
{
    public Annotations
    {
        audience = ImmutableList.copyOf(audience);
        requireNonNull(priority, "priority is null");
    }
}
