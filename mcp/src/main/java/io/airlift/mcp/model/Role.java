package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ENGLISH;

public enum Role
{
    USER,
    ASSISTANT;

    @JsonValue
    public String toJsonValue()
    {
        return name().toLowerCase(ENGLISH);
    }
}
