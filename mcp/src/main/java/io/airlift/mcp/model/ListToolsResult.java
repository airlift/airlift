package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record ListToolsResult(Optional<ResultType> resultType, List<Tool> tools, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult<ListToolsResult>,
                   Meta<ListToolsResult>,
                   PaginatedResult
{
    public ListToolsResult
    {
        resultType = requireNonNullElse(resultType, Optional.empty());
        tools = ImmutableList.copyOf(tools);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = normalize(meta);
    }

    public ListToolsResult(List<Tool> tools)
    {
        this(Optional.empty(), tools, Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    public ListToolsResult(List<Tool> tools, Optional<String> nextCursor)
    {
        this(Optional.empty(), tools, nextCursor, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ListToolsResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ListToolsResult(Optional.of(ResultType.COMPLETE), tools, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public ListToolsResult withMeta(Map<String, Object> meta)
    {
        return new ListToolsResult(resultType, tools, nextCursor, ttlMs, cacheScope, Optional.of(meta));
    }
}
