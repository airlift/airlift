package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record IncompleteToolResult(String status, Optional<Map<String, InputRequest>> inputRequests, Optional<String> requestState)
        implements CallToolResult
{
    public IncompleteToolResult
    {
        requireNonNull(status, "status is null");
        inputRequests = requireNonNullElse(inputRequests, Optional.<Map<String, InputRequest>>empty()).map(ImmutableMap::copyOf);
        requestState = requireNonNullElse(requestState, Optional.empty());
    }
}
