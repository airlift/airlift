package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonValue;

public record StructuredContent<T>(@JsonValue T value) {
    public StructuredContent {
        requireNonNull(value, "value is null");
    }
}
