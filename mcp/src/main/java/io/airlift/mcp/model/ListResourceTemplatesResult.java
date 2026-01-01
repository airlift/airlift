package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;

public record ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates, Optional<String> nextCursor)
        implements PaginatedResult
{
    public ListResourceTemplatesResult
    {
        resourceTemplates = ImmutableList.copyOf(resourceTemplates);
        nextCursor = firstNonNull(nextCursor, Optional.empty());
    }

    public ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates)
    {
        this(resourceTemplates, Optional.empty());
    }
}
