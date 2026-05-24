package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.ResultType.INPUT_REQUIRED;
import static java.util.Objects.requireNonNullElse;

public record ReadResourceResult(
        Optional<List<ResourceContents>> contents,
        OptionalInt ttlMs,
        Optional<CacheScope> cacheScope,
        Optional<ResultType> resultType,
        Optional<String> requestState,
        Optional<Map<String, InputRequest>> inputRequests,
        Optional<Map<String, Object>> meta)
        implements CacheableResult,
                   InputRequests,
                   Meta
{
    public static InputRequests.Builder<ReadResourceResult> inputRequestsBuilder()
    {
        return new Builder<>()
        {
            @Override
            public ReadResourceResult build()
            {
                return new ReadResourceResult(
                        Optional.empty(),
                        OptionalInt.empty(),
                        Optional.empty(),
                        Optional.of(INPUT_REQUIRED),
                        requestState,
                        Optional.of(inputRequests.buildOrThrow()),
                        Optional.empty());
            }
        };
    }

    public ReadResourceResult
    {
        contents = requireNonNullElse(contents, Optional.empty());
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        resultType = requireNonNullElse(resultType, Optional.empty());
        requestState = requireNonNullElse(requestState, Optional.empty());
        inputRequests = requireNonNullElse(inputRequests, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public ReadResourceResult(List<ResourceContents> contents)
    {
        this(Optional.of(contents), OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ReadResourceResult(ResourceContents contents)
    {
        this(Optional.of(ImmutableList.of(contents)), OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ReadResourceResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ReadResourceResult(contents, OptionalInt.of(ttlMs), Optional.of(cacheScope), resultType, requestState, inputRequests, meta);
    }

    @Override
    public ReadResourceResult withMeta(Map<String, Object> meta)
    {
        return new ReadResourceResult(contents, ttlMs, cacheScope, resultType, requestState, inputRequests, Optional.of(meta));
    }

    @Override
    public ReadResourceResult withInputRequests(Optional<ResultType> resultType, Optional<String> requestState, Optional<Map<String, InputRequest>> inputRequests)
    {
        return new ReadResourceResult(contents, ttlMs, cacheScope, resultType, requestState, inputRequests, meta);
    }
}
