package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNullElse;

public record InputResponses(Map<String, InputResponse> inputResponses, Optional<String> requestState)
{
    public static final InputResponses EMPTY = new InputResponses(Map.of(), Optional.empty());

    public InputResponses
    {
        inputResponses = ImmutableMap.copyOf(inputResponses);
        requestState = requireNonNullElse(requestState, Optional.empty());
    }

    public <R extends InputResponse> Optional<R> response(String key, Class<R> type)
    {
        return Optional.ofNullable(inputResponses.get(key))
                .map(type::cast);
    }
}
