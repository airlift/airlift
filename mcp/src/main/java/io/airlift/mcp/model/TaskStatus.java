package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ROOT;

public enum TaskStatus
{
    WORKING,
    INPUT_REQUIRED,
    COMPLETED,
    FAILED,
    CANCELLED;

    @JsonValue
    public String toJsonValue()
    {
        return name().toLowerCase(ROOT);
    }
}
