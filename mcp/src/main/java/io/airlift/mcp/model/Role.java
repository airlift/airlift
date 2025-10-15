package io.airlift.mcp.model;

import static java.util.Locale.ENGLISH;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Role {
    USER,
    ASSISTANT;

    @JsonValue
    public String toJsonValue() {
        return name().toLowerCase(ENGLISH);
    }
}
