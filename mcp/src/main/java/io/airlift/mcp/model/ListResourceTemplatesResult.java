package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNullElse;

public record ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult,
                   Meta<ListResourceTemplatesResult>,
                   PaginatedResult
{
    public ListResourceTemplatesResult
    {
        resourceTemplates = ImmutableList.copyOf(resourceTemplates);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates)
    {
        this(resourceTemplates, Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    public ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates, Optional<String> nextCursor)
    {
        this(resourceTemplates, nextCursor, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ListResourceTemplatesResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ListResourceTemplatesResult(resourceTemplates, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public ListResourceTemplatesResult withMeta(Map<String, Object> meta)
    {
        return new ListResourceTemplatesResult(resourceTemplates, nextCursor, ttlMs, cacheScope, Optional.of(meta));
    }
}
