package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;

public record ListResourcesResult(List<Resource> resources, Optional<String> nextCursor)
        implements PaginatedResult
{
    public ListResourcesResult
    {
        resources = ImmutableList.copyOf(resources);
        nextCursor = firstNonNull(nextCursor, Optional.empty());
    }

    public ListResourcesResult(List<Resource> resources)
    {
        this(resources, Optional.empty());
    }
}
