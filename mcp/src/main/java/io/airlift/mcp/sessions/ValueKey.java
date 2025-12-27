package io.airlift.mcp.sessions;

import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.versions.Versions;

import static java.util.Objects.requireNonNull;

public record ValueKey<T>(String name, Class<T> type)
{
    public static final ValueKey<LoggingLevel> LOGGING_LEVEL = of(LoggingLevel.class);
    public static final ValueKey<Versions> SESSION_VERSIONS = of(Versions.class);
    public static final ValueKey<ClientCapabilities> CLIENT_CAPABILITIES = of(ClientCapabilities.class);

    public ValueKey
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
    }

    public static ValueKey<CancelledNotification> cancellationKey(Object requestId)
    {
        return new ValueKey<>("cancellation-notification-" + requestId, CancelledNotification.class);
    }

    @SuppressWarnings("rawtypes")
    public static ValueKey<JsonRpcResponse> serverToClientResponseKey(Object requestId)
    {
        return new ValueKey<>("server-to-client-request-" + requestId, JsonRpcResponse.class);
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
