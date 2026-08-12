package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record ListResourcesResult(Optional<ResultType> resultType, List<Resource> resources, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult<ListResourcesResult>,
                   Meta<ListResourcesResult>,
                   PaginatedResult
{
    public ListResourcesResult
    {
        resultType = requireNonNullElse(resultType, Optional.empty());
        resources = ImmutableList.copyOf(resources);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = normalize(meta);
    }

    public ListResourcesResult(List<Resource> resources, Optional<String> nextCursor)
    {
        this(Optional.empty(), resources, nextCursor, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    public ListResourcesResult(List<Resource> resources)
    {
        this(Optional.empty(), resources, Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ListResourcesResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ListResourcesResult(Optional.of(ResultType.COMPLETE), resources, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public ListResourcesResult withMeta(Map<String, Object> meta)
    {
        return new ListResourcesResult(resultType, resources, nextCursor, ttlMs, cacheScope, Optional.of(meta));
    }
}
