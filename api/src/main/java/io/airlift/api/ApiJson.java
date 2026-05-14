package io.airlift.api;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;

public sealed interface ApiJson<T extends JsonNode>
        permits ApiJsonList,
                ApiJsonNode,
                ApiJsonObject
{
    @JsonValue
    T value();
}
