package io.airlift.mcp.sessions;

import io.airlift.mcp.model.LoggingLevel;

import static java.util.Objects.requireNonNull;

public record SessionValueKey<T>(String name, Class<T> type)
{
    public static final SessionValueKey<LoggingLevel> LOGGING_LEVEL = of(LoggingLevel.class);

    public SessionValueKey
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
    }

    public static <T> SessionValueKey<T> of(String name, Class<T> type)
    {
        return new SessionValueKey<>(name, type);
    }

    public static <T> SessionValueKey<T> of(Class<T> type)
    {
        return new SessionValueKey<>(type.getName(), type);
    }
}
