package io.airlift.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import tools.jackson.databind.node.ObjectNode;

import static com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING;
import static java.util.Objects.requireNonNull;

public record ApiJsonObject(ObjectNode value)
        implements ApiJson<ObjectNode>
{
    @JsonCreator(mode = DELEGATING)
    public ApiJsonObject
    {
        requireNonNull(value, "value is null");
    }
}
