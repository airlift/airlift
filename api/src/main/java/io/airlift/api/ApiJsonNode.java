package io.airlift.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.JsonNode;

import static com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING;
import static java.util.Objects.requireNonNull;

public record ApiJsonNode(JsonNode value)
        implements ApiJson<JsonNode>
{
    @JsonCreator(mode = DELEGATING)
    public ApiJsonNode
    {
        requireNonNull(value, "value is null");
    }
}
