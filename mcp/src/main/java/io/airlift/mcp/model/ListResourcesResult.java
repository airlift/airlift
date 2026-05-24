package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNullElse;

public record ListResourcesResult(List<Resource> resources, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope)
        implements PaginatedResult, CacheableResult
{
    public ListResourcesResult
    {
        resources = ImmutableList.copyOf(resources);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
    }

    public ListResourcesResult(List<Resource> resources, Optional<String> nextCursor)
    {
        this(resources, nextCursor, OptionalInt.empty(), Optional.empty());
    }

    public ListResourcesResult(List<Resource> resources)
    {
        this(resources, Optional.empty(), OptionalInt.empty(), Optional.empty());
    }

    @Override
    public ListResourcesResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ListResourcesResult(resources, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope));
    }
}
