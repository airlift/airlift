package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Objects.requireNonNull;

public record StructuredContent<T>(@JsonValue T value)
{
    public StructuredContent
    {
        requireNonNull(value, "value is null");
    }
}
