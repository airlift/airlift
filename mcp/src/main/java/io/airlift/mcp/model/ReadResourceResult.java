package io.airlift.mcp.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.Meta.normalize;
import static io.airlift.mcp.model.ResultType.COMPLETE;
import static io.airlift.mcp.model.ResultType.INPUT_REQUIRED;
import static java.util.Objects.requireNonNullElse;

@SuppressWarnings("unused")
public record ReadResourceResult(
        Optional<ResultType> resultType,
        Optional<List<ResourceContents>> contents,
        OptionalInt ttlMs,
        Optional<CacheScope> cacheScope,
        Optional<String> requestState,
        Optional<Map<String, InputRequest>> inputRequests,
        Optional<Map<String, Object>> meta)
        implements CacheableResult<ReadResourceResult>,
                   InputRequests<ReadResourceResult>,
                   Meta<ReadResourceResult>
{
    private static final Factory<ReadResourceResult> FACTORY = (requestState, inputRequests) -> new ReadResourceResult(
            Optional.of(INPUT_REQUIRED),
            Optional.empty(),
            OptionalInt.empty(),
            Optional.empty(),
            requestState,
            Optional.of(inputRequests),
            Optional.empty());

    public static InputRequests.Builder<ReadResourceResult> inputRequestsBuilder()
    {
        return InputRequests.builder(FACTORY);
    }

    public ReadResourceResult
    {
        resultType = requireNonNullElse(resultType, Optional.empty());
        contents = requireNonNullElse(contents, Optional.<List<ResourceContents>>empty()).map(ImmutableList::copyOf);
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        cacheScope = requireNonNullElse(cacheScope, Optional.empty());
        requestState = requireNonNullElse(requestState, Optional.empty());
        inputRequests = requireNonNullElse(inputRequests, Optional.<Map<String, InputRequest>>empty()).map(ImmutableMap::copyOf);
        meta = normalize(meta);
    }

    public ReadResourceResult(List<ResourceContents> contents)
    {
        this(Optional.empty(), Optional.of(contents), OptionalInt.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Override
    public ReadResourceResult withCacheableResult(int ttlMs, CacheScope cacheScope)
    {
        return new ReadResourceResult(Optional.of(COMPLETE), contents, OptionalInt.of(ttlMs), Optional.of(cacheScope), requestState, inputRequests, meta);
    }

    @Override
    public ReadResourceResult withMeta(Map<String, Object> meta)
    {
        return new ReadResourceResult(resultType, contents, ttlMs, cacheScope, requestState, inputRequests, Optional.of(meta));
    }

    @Override
    public ReadResourceResult withInputRequests(Optional<ResultType> resultType, Optional<String> requestState, Optional<Map<String, InputRequest>> inputRequests)
    {
        return new ReadResourceResult(resultType, contents, ttlMs, cacheScope, requestState, inputRequests, meta);
    }
}
