package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

public sealed interface InputResponses<T extends InputResponses<T>>
        permits CallToolRequest,
                EmptyInputResponses,
                GetPromptRequest,
                ReadResourceRequest,
                TaskInputResponses
{
    InputResponses<?> EMPTY = new EmptyInputResponses();

    @JsonProperty
    default Optional<String> requestState()
    {
        return Optional.empty();
    }

    @JsonProperty
    Optional<Map<String, Object>> inputResponses();

    @JsonIgnore
    default Map<String, Object> inputResponsesMap()
    {
        return inputResponses().orElseGet(ImmutableMap::of);
    }

    @JsonIgnore
    default Optional<Object> getInputResponse(String key)
    {
        return inputResponses().flatMap(responses -> Optional.ofNullable(responses.get(key)));
    }

    @JsonIgnore
    T withInputResponses(Optional<String> requestState, Map<String, Object> inputResponses);
}
