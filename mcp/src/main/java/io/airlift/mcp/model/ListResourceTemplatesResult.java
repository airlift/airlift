package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates, Optional<String> nextCursor)
        implements PaginatedResult
{
    public ListResourceTemplatesResult
    {
        resourceTemplates = ImmutableList.copyOf(resourceTemplates);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
    }

    public ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates)
    {
        this(resourceTemplates, Optional.empty());
    }
}
