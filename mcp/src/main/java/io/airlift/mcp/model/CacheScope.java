package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ROOT;

public enum CacheScope
{
    PUBLIC,
    PRIVATE;

    @JsonValue
    public String toJsonValue()
    {
        return name().toLowerCase(ROOT);
    }

    @JsonCreator
    public static CacheScope fromJsonValue(String value)
    {
        return CacheScope.valueOf(value.toUpperCase(ROOT));
    }
}
