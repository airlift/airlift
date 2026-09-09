package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record ListPromptsResult(List<Prompt> prompts, Optional<String> nextCursor, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult<ListPromptsResult>,
                   Meta<ListPromptsResult>,
                   PaginatedResult
{
    public ListPromptsResult
    {
        prompts = ImmutableList.copyOf(prompts);
        nextCursor = requireNonNullElse(nextCursor, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = normalize(meta);
    }

    public ListPromptsResult(List<Prompt> prompts)
    {
        this(prompts, Optional.empty(), OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    public ListPromptsResult(List<Prompt> prompts, Optional<String> nextCursor)
    {
        this(prompts, nextCursor, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ListPromptsResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ListPromptsResult(prompts, nextCursor, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public ListPromptsResult withMeta(Map<String, Object> meta)
    {
        return new ListPromptsResult(prompts, nextCursor, ttlMs, cacheScope, Optional.of(meta));
    }
}
