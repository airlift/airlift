package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record InputResponses(Map<String, InputResponse> inputResponses, Optional<String> requestState)
{
    public static final InputResponses EMPTY = new InputResponses(ImmutableMap.of(), Optional.empty());

    public InputResponses
    {
        inputResponses = ImmutableMap.copyOf(inputResponses);
        requireNonNull(requestState, "requestState is null");
    }

    public <R extends InputResponse> Optional<R> response(String key, Class<R> clazz)
    {
        return Optional.ofNullable(inputResponses.get(key))
                .map(clazz::cast);
    }
}
