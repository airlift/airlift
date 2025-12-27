package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListToolsResult(List<Tool> tools, Optional<String> nextCursor)
        implements PaginatedResult
{
    public ListToolsResult
    {
        tools = ImmutableList.copyOf(tools);
        requireNonNull(nextCursor, "nextCursor is null");
    }

    public ListToolsResult(List<Tool> tools)
    {
        this(tools, Optional.empty());
    }
}
