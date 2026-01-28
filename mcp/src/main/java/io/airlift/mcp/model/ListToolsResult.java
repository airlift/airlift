package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record ListToolsResult(List<Tool> tools, Optional<String> nextCursor)
        implements PaginatedResult
{
    public ListToolsResult
    {
        tools = ImmutableList.copyOf(tools);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
    }

    public ListToolsResult(List<Tool> tools)
    {
        this(tools, Optional.empty());
    }
}
