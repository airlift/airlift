package io.airlift.mcp.sessions;

import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.versions.Versions;

import static java.util.Objects.requireNonNull;

public record ValueKey<T>(String name, Class<T> type)
{
    public static final ValueKey<LoggingLevel> LOGGING_LEVEL = of(LoggingLevel.class);
    public static final ValueKey<Versions> SESSION_VERSIONS = of(Versions.class);

    public ValueKey
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
    }

    public static ValueKey<CancelledNotification> cancellationKey(Object requestId)
    {
        return new ValueKey<>("cancellation-notification-" + requestId, CancelledNotification.class);
    }

    public static <T> ValueKey<T> of(String name, Class<T> type)
    {
        return new ValueKey<>(name, type);
    }

    public static <T> ValueKey<T> of(Class<T> type)
    {
        return new ValueKey<>(type.getName(), type);
    }
}
