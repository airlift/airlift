package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.ResultType.INCOMPLETE;
import static java.util.Objects.requireNonNullElse;

public record InputRequests(Map<String, InputRequest> inputRequests, Optional<String> requestState, Optional<Map<String, Object>> meta)
        implements CallToolResponse, ReadResourceResponse, GetPromptResponse
{
    public InputRequests
    {
        inputRequests = ImmutableMap.copyOf(inputRequests);
        requestState = requireNonNullElse(requestState, Optional.empty());
        meta = requireNonNullElse(meta, Optional.empty());
    }

    public InputRequests(String key, InputRequest request)
    {
        this(ImmutableMap.of(key, request), Optional.empty(), Optional.empty());
    }

    @Override
    public Optional<ResultType> resultType()
    {
        return Optional.of(INCOMPLETE);
    }

    public InputRequests withRequestState(String requestState)
    {
        return new InputRequests(inputRequests, Optional.of(requestState), meta);
    }

    @Override
    public InputRequests withMeta(Map<String, Object> meta)
    {
        return new InputRequests(inputRequests, requestState, Optional.of(meta));
    }
}
