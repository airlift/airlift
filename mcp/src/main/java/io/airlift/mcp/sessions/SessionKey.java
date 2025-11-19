package io.airlift.mcp.sessions;

import io.airlift.mcp.model.LoggingLevel;

import static java.util.Objects.requireNonNull;

public record SessionKey<T>(String name, Class<T> type)
{
    public static final SessionKey<LoggingLevel> LOGGING_LEVEL = of(LoggingLevel.class);

    public SessionKey
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
    }

    public static <T> SessionKey<T> of(String name, Class<T> type)
    {
        return new SessionKey<>(name, type);
    }

    public static <T> SessionKey<T> of(Class<T> type)
    {
        return new SessionKey<>(type.getName(), type);
    }
}
