package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListToolsResponse(List<Tool> tools, Optional<String> nextCursor)
        implements Paginated
{
    public ListToolsResponse
    {
        tools = ImmutableList.copyOf(tools);
        requireNonNull(nextCursor, "nextCursor is null");
    }
}
