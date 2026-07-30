package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNullElse;

public record ReadResourceResult(List<ResourceContents> contents, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult, Meta
{
    public ReadResourceResult
    {
        contents = ImmutableList.copyOf(contents);
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public ReadResourceResult(List<ResourceContents> contents)
    {
        this(contents, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ReadResourceResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ReadResourceResult(contents, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public ReadResourceResult withMeta(Map<String, Object> meta)
    {
        return new ReadResourceResult(contents, ttlMs, cacheScope, Optional.of(meta));
    }
}
