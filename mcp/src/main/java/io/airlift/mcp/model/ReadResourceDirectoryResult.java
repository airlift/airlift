package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceDirectoryResult(List<Resource> resources, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult<ReadResourceDirectoryResult>,
                   Meta<ReadResourceDirectoryResult>,
                   PaginatedResult
{
    public ReadResourceDirectoryResult
    {
        resources = ImmutableList.copyOf(resources);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = normalize(meta);
    }

    public ReadResourceDirectoryResult(List<Resource> resources, Optional<String> nextCursor)
    {
        this(resources, nextCursor, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ReadResourceDirectoryResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ReadResourceDirectoryResult(resources, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public ReadResourceDirectoryResult withMeta(Map<String, Object> meta)
    {
        return new ReadResourceDirectoryResult(resources, nextCursor, ttlMs, cacheScope, Optional.of(meta));
    }
}
