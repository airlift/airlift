package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ROOT;

public enum ResultType
{
    COMPLETE,
    INPUT_REQUIRED,
    TASK;

    @JsonValue
    public String toJsonValue()
    {
        return name().toLowerCase(ROOT);
    }

    @JsonCreator
    public static ResultType fromJsonValue(String value)
    {
        return ResultType.valueOf(value.toUpperCase(ROOT));
    }
}
