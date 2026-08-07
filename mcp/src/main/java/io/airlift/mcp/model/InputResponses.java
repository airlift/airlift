package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

public interface InputResponses
{
    InputResponses EMPTY = new InputResponses()
    {
        @Override
        public Optional<String> requestState()
        {
            return Optional.empty();
        }

        @Override
        public Optional<Map<String, Object>> inputResponses()
        {
            return Optional.empty();
        }
    };

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
}
