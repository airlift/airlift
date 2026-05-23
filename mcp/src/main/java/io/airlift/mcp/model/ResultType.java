package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ROOT;

public enum ResultType
{
    COMPLETE,
    INPUT_REQUIRED;

    @JsonValue
    public String toJsonValue()
    {
        return name().toLowerCase(ROOT);
    }

    @JsonCreator
    public static Role fromJsonValue(String value)
    {
        return Role.valueOf(value.toUpperCase(ROOT));
    }
}
