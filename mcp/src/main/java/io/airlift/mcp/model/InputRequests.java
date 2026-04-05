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
}
