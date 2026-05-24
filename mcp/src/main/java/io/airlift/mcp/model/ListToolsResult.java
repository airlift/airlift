package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNullElse;

public record ListToolsResult(List<Tool> tools, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope)
        implements PaginatedResult, CacheableResult
{
    public ListToolsResult
    {
        tools = ImmutableList.copyOf(tools);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
    }

    public ListToolsResult(List<Tool> tools)
    {
        this(tools, Optional.empty(), OptionalInt.empty(), Optional.empty());
    }

    public ListToolsResult(List<Tool> tools, Optional<String> nextCursor)
    {
        this(tools, nextCursor, OptionalInt.empty(), Optional.empty());
    }

    @Override
    public ListToolsResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ListToolsResult(tools, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope));
    }
}
