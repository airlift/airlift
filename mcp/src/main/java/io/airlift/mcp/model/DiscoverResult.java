package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record DiscoverResult(
        ResultType resultType,
        List<String> supportedVersions,
        ServerCapabilities capabilities,
        Optional<String> instructions,
        OptionalInt ttlMs,
        Optional<CacheScope> cacheScope,
        Optional<Map<String, Object>> meta)
        implements Meta<DiscoverResult>, CacheableResult<DiscoverResult>
{
    public DiscoverResult
    {
        requireNonNull(resultType, "resultType is null");
        supportedVersions = ImmutableList.copyOf(supportedVersions);
        requireNonNull(capabilities, "capabilities is null");
        instructions = requireNonNullElse(instructions, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public DiscoverResult withMeta(Map<String, Object> meta)
    {
        return new DiscoverResult(resultType, supportedVersions, capabilities, instructions, ttlMs, cacheScope, Optional.of(meta));
    }

    @Override
    public DiscoverResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new DiscoverResult(ResultType.COMPLETE, supportedVersions, capabilities, instructions, OptionalInt.of(ttlMs), Optional.of(cacheScope), meta);
    }
}
