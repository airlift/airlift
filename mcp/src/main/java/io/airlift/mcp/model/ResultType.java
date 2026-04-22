package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ROOT;

public enum ResultType
{
    COMPLETE,
    INCOMPLETE;

    @JsonValue
    public String toJsonValue()
    {
        return name().toLowerCase(ROOT);
    }
}
