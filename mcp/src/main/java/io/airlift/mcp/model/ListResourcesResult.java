package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ListResourcesResult(List<Resource> resources, Optional<String> nextCursor)
        implements Paginated
{
    public ListResourcesResult
    {
        resources = ImmutableList.copyOf(resources);
        requireNonNull(nextCursor, "nextCursor is null");
    }
}
