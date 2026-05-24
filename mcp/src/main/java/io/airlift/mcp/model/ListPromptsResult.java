package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNullElse;

public record ListPromptsResult(List<Prompt> prompts, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope)
        implements PaginatedResult, CacheableResult
{
    public ListPromptsResult
    {
        prompts = ImmutableList.copyOf(prompts);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
    }

    public ListPromptsResult(List<Prompt> prompts)
    {
        this(prompts, Optional.empty(), OptionalInt.empty(), Optional.empty());
    }

    public ListPromptsResult(List<Prompt> prompts, Optional<String> nextCursor)
    {
        this(prompts, nextCursor, OptionalInt.empty(), Optional.empty());
    }

    @Override
    public ListPromptsResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ListPromptsResult(prompts, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope));
    }
}
