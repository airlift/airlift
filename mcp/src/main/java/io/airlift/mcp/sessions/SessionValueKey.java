package io.airlift.mcp.sessions;

import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.versions.ResourceVersion;
import io.airlift.mcp.versions.SystemListVersions;

import static java.util.Objects.requireNonNull;

public record SessionValueKey<T>(String name, Class<T> type)
{
    public static final SessionValueKey<LoggingLevel> LOGGING_LEVEL = of(LoggingLevel.class);
    public static final SessionValueKey<SystemListVersions> SYSTEM_LIST_VERSIONS = of(SystemListVersions.class);
    public static final SessionValueKey<ClientCapabilities> CLIENT_CAPABILITIES = of(ClientCapabilities.class);

    public SessionValueKey
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
    }

    @SuppressWarnings("rawtypes")
    public static SessionValueKey<JsonRpcResponse> serverToClientResponseKey(Object requestId)
    {
        return new SessionValueKey<>("server-to-client-request-" + requestId, JsonRpcResponse.class);
    }

    public static SessionValueKey<CancelledNotification> cancellationKey(Object requestId)
    {
        return new SessionValueKey<>("cancellation-notification-" + requestId, CancelledNotification.class);
    }

    public static SessionValueKey<ResourceVersion> resourceVersionKey(String uri)
    {
        return of(uri, ResourceVersion.class);
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
