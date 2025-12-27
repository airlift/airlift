package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListResourcesResult(List<Resource> resources, Optional<String> nextCursor)
        implements PaginatedResult
{
    public ListResourcesResult
    {
        resources = ImmutableList.copyOf(resources);
        requireNonNull(nextCursor, "nextCursor is null");
    }

    public ListResourcesResult(List<Resource> resources)
    {
        this(resources, Optional.empty());
    }
}
