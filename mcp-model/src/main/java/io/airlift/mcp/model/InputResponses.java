package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

@SuppressWarnings("rawtypes")
public interface InputResponses<T extends InputResponses<T>>
{
    InputResponses<?> EMPTY = new InputResponses()
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

        @Override
        public InputResponses withInputResponses(Optional requestState, Map inputResponses)
        {
            throw new UnsupportedOperationException();
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

    @JsonIgnore
    T withInputResponses(Optional<String> requestState, Map<String, Object> inputResponses);
}
