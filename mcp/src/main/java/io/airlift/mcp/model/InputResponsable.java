package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

public interface InputResponsable<T extends InputResponsable<T>>
{
    @JsonProperty
    Optional<Map<String, InputResponse>> inputResponses();

    @JsonProperty
    Optional<String> requestState();

    default Optional<InputResponses> toInputResponses()
    {
        return inputResponses()
                .map(responses -> new InputResponses(responses, requestState()));
    }

    T withInputResponses(InputResponses inputResponses);
}
