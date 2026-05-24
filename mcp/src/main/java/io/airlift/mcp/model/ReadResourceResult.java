package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNullElse;

public record ReadResourceResult(List<ResourceContents> contents, OptionalInt ttlMs, Optional<CacheScope> cacheScope)
        implements CacheableResult
{
    public ReadResourceResult
    {
        contents = ImmutableList.copyOf(contents);
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
    }

    public ReadResourceResult(List<ResourceContents> contents)
    {
        this(contents, OptionalInt.empty(), Optional.empty());
    }

    @Override
    public ReadResourceResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ReadResourceResult(contents, OptionalInt.of(ttlMs), Optional.of(cacheScope));
    }
}
