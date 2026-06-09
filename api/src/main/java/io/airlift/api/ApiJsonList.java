package io.airlift.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.node.ArrayNode;

import static com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING;
import static java.util.Objects.requireNonNull;

public record ApiJsonList(ArrayNode value)
        implements ApiJson<ArrayNode>
{
    @JsonCreator(mode = DELEGATING)
    public ApiJsonList
    {
        requireNonNull(value, "value is null");
    }
}
