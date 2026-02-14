package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ROOT;

public enum ToolExecution
{
    UNDEFINED,
    FORBIDDEN,
    OPTIONAL,
    REQUIRED;

    @JsonValue
    public String toJsonValue()
    {
        return (this == UNDEFINED) ? FORBIDDEN.toJsonValue() : name().toLowerCase(ROOT);
    }
}
