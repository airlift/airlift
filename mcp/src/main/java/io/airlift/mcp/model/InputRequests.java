package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record InputRequests(Map<String, InputRequest> inputRequests, Optional<String> requestState)
        implements CallToolResult
{
    public InputRequests
    {
        inputRequests = ImmutableMap.copyOf(inputRequests);
        requireNonNull(requestState, "requestState is null");
    }

    public static InputRequests inputRequest(String key, InputRequest inputRequest)
    {
        return new InputRequests(Map.of(key, inputRequest), Optional.empty());
    }

    public InputRequests withInputRequest(String key, InputRequest inputRequest)
    {
        ImmutableMap<String, InputRequest> updatedInputRequests = ImmutableMap.<String, InputRequest>builder()
                .putAll(inputRequests)
                .put(key, inputRequest)
                .build();
        return new InputRequests(updatedInputRequests, requestState);
    }

    public InputRequests withRequestState(String requestState)
    {
        return new InputRequests(inputRequests, Optional.of(requestState));
    }
}
