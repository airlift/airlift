package io.airlift.mcp.model;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates, Optional<String> nextCursor)
        implements Paginated
{
    public ListResourceTemplatesResult
    {
        resourceTemplates = List.copyOf(resourceTemplates);
        requireNonNull(nextCursor, "nextCursor is null");
    }
}
