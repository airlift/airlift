package io.airlift.mcp.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record JsonSchema(
        String type,
        Optional<Map<String, Object>> properties,
        Optional<List<String>> required,
        Optional<Boolean> additionalProperties,
        Optional<Map<String, Object>> defs,
        Optional<Map<String, Object>> definitions)
{
    public JsonSchema
    {
        requireNonNull(type, "type is null");
        requireNonNull(properties, "properties is null");
        requireNonNull(required, "required is null");
        requireNonNull(additionalProperties, "additionalProperties is null");
        requireNonNull(defs, "defs is null");
        requireNonNull(definitions, "definitions is null");
    }
}
