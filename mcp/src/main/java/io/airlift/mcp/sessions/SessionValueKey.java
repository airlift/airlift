package io.airlift.mcp.sessions;

import io.airlift.mcp.SentMessages;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListRootsResult;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.storage.StorageKeyId;
import io.airlift.mcp.versions.ResourceVersions;
import io.airlift.mcp.versions.SystemListVersions;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record SessionValueKey<T>(String name, Class<T> type)
{
    private static final String KEY_SEPARATOR = "|";

    public static final SessionValueKey<LoggingLevel> LOGGING_LEVEL = of(LoggingLevel.class);
    public static final SessionValueKey<SystemListVersions> SYSTEM_LIST_VERSIONS = of(SystemListVersions.class);
    public static final SessionValueKey<ClientCapabilities> CLIENT_CAPABILITIES = of(ClientCapabilities.class);
    public static final SessionValueKey<ListRootsResult> ROOTS = of(ListRootsResult.class);
    public static final SessionValueKey<Protocol> PROTOCOL = of(Protocol.class);
    public static final SessionValueKey<SentMessages> SENT_MESSAGES = of(SentMessages.class);
    public static final SessionValueKey<ResourceVersions> RESOURCE_VERSIONS = of(ResourceVersions.class);

    public SessionValueKey
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");

        checkArgument(!type.getName().contains(KEY_SEPARATOR), "Key type name cannot contain the separator character: " + KEY_SEPARATOR);
        checkArgument(!name.contains(KEY_SEPARATOR), "Key name cannot contain the separator character: " + KEY_SEPARATOR);
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

    public static <T> SessionValueKey<T> of(String name, Class<T> type)
    {
        return new SessionValueKey<>(name, type);
    }

    public static <T> SessionValueKey<T> of(Class<T> type)
    {
        return new SessionValueKey<>(type.getName(), type);
    }

    StorageKeyId toKeyId()
    {
        return new StorageKeyId(type.getName() + KEY_SEPARATOR + name);
    }
}
