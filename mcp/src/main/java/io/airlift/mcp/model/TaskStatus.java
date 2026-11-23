package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ROOT;

public enum TaskStatus
{
    WORKING,
    INPUT_REQUIRED,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal()
    {
        return switch (this) {
            case WORKING, INPUT_REQUIRED -> false;
            case COMPLETED, FAILED, CANCELLED -> true;
        };
    }

    @JsonValue
    public String toJsonValue()
    {
        return name().toLowerCase(ROOT);
    }

    @JsonCreator
    public static TaskStatus fromJsonValue(String value)
    {
        return TaskStatus.valueOf(value.toUpperCase(ROOT));
    }
}
