package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.json.JsonMapper;

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
    Optional<String> requestState();

    @JsonProperty
    Optional<Map<String, Object>> inputResponses();

    @JsonIgnore
    default <T> Optional<T> mapResponse(JsonMapper jsonMapper, String key, Class<T> responseType)
    {
        return inputResponses().flatMap(responses -> Optional.ofNullable(responses.get(key)))
                .map(value -> jsonMapper.convertValue(value, responseType));
    }
}
