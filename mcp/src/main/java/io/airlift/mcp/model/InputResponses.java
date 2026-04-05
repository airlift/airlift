package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record InputResponses(Map<String, InputResponse> inputResponses, Optional<String> requestState)
{
    public InputResponses
    {
        inputResponses = ImmutableMap.copyOf(inputResponses);
        requireNonNull(requestState, "requestState is null");
    }
}
