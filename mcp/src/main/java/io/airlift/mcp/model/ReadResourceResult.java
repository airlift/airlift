package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceResult(Optional<ResultType> resultType, List<ResourceContents> contents, OptionalInt ttlMs, Optional<CacheScope> cacheScope, Optional<Map<String, Object>> meta)
        implements CacheableResult<ReadResourceResult>, Meta<ReadResourceResult>
{
    public ReadResourceResult
    {
        resultType = requireNonNullElse(resultType, Optional.empty());
        contents = ImmutableList.copyOf(contents);
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = normalize(meta);
    }

    public ReadResourceResult(List<ResourceContents> contents)
    {
        this(Optional.empty(), contents, OptionalInt.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ReadResourceResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ReadResourceResult(Optional.of(ResultType.COMPLETE), contents, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }

    @Override
    public ReadResourceResult withMeta(Map<String, Object> meta)
    {
        return new ReadResourceResult(resultType, contents, ttlMs, cacheScope, Optional.of(meta));
    }
}
