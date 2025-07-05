package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record Completion(List<String> values, Optional<Integer> total, boolean hasMore)
{
    public Completion
    {
        values = ImmutableList.copyOf(values);
        requireNonNull(total, "total is null");
    }

    public Completion(List<String> values)
    {
        this(values, Optional.empty(), false);
    }
}
