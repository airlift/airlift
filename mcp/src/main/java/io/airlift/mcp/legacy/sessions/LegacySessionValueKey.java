package io.airlift.mcp.legacy.sessions;

import io.airlift.mcp.SentMessages;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListRootsResult;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.versions.ResourceVersions;
import io.airlift.mcp.versions.SystemListVersions;

import static java.util.Objects.requireNonNull;

public record LegacySessionValueKey<T>(String name, Class<T> type)
{
    public static final LegacySessionValueKey<LoggingLevel> LOGGING_LEVEL = of(LoggingLevel.class);
    public static final LegacySessionValueKey<SystemListVersions> SYSTEM_LIST_VERSIONS = of(SystemListVersions.class);
    public static final LegacySessionValueKey<ClientCapabilities> CLIENT_CAPABILITIES = of(ClientCapabilities.class);
    public static final LegacySessionValueKey<ListRootsResult> ROOTS = of(ListRootsResult.class);
    public static final LegacySessionValueKey<Protocol> PROTOCOL = of(Protocol.class);
    public static final LegacySessionValueKey<SentMessages> SENT_MESSAGES = of(SentMessages.class);
    public static final LegacySessionValueKey<ResourceVersions> RESOURCE_VERSIONS = of(ResourceVersions.class);

    public LegacySessionValueKey
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
    }

    @SuppressWarnings("rawtypes")
    public static LegacySessionValueKey<JsonRpcResponse> serverToClientResponseKey(Object requestId)
    {
        return new LegacySessionValueKey<>("server-to-client-request-" + requestId, JsonRpcResponse.class);
    }

    public static LegacySessionValueKey<CancelledNotification> cancellationKey(Object requestId)
    {
        return new LegacySessionValueKey<>("cancellation-notification-" + requestId, CancelledNotification.class);
    }

    public static <T> LegacySessionValueKey<T> of(String name, Class<T> type)
    {
        return new LegacySessionValueKey<>(name, type);
    }

    public static <T> LegacySessionValueKey<T> of(Class<T> type)
    {
        return new LegacySessionValueKey<>(type.getName(), type);
    }
}
