package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record ListResourceTemplatesResult(Optional<ResultType> resultType, List<ResourceTemplate> resourceTemplates, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult<ListResourceTemplatesResult>,
                   Meta<ListResourceTemplatesResult>,
                   PaginatedResult
{
    public ListResourceTemplatesResult
    {
        resultType = requireNonNullElse(resultType, Optional.empty());
        resourceTemplates = ImmutableList.copyOf(resourceTemplates);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = normalize(meta);
    }

    public ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates)
    {
        this(Optional.empty(), resourceTemplates, Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    public ListResourceTemplatesResult(List<ResourceTemplate> resourceTemplates, Optional<String> nextCursor)
    {
        this(Optional.empty(), resourceTemplates, nextCursor, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ListResourceTemplatesResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ListResourceTemplatesResult(Optional.of(ResultType.COMPLETE), resourceTemplates, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public ListResourceTemplatesResult withMeta(Map<String, Object> meta)
    {
        return new ListResourceTemplatesResult(resultType, resourceTemplates, nextCursor, ttlMs, cacheScope, Optional.of(meta));
    }
}
