package io.airlift.mcp.session;

import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.RootsList;

import static java.util.Objects.requireNonNull;

public record McpValueKey<T>(String name, Class<T> type)
{
    public static final McpValueKey<LoggingLevel> LOGGING_LEVEL = new McpValueKey<>(LoggingLevel.class.getName(), LoggingLevel.class);
    public static final McpValueKey<ClientCapabilities> CLIENT_CAPABILITIES = new McpValueKey<>(ClientCapabilities.class.getName(), ClientCapabilities.class);
    public static final McpValueKey<Implementation> CLIENT_INFO = new McpValueKey<>(Implementation.class.getName() + ".client", Implementation.class);
    public static final McpValueKey<RootsList> ROOTS = new McpValueKey<>(RootsList.class.getName(), RootsList.class);

    public McpValueKey
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
    }

    public static McpValueKey<Boolean> resourceSubscription(String uri)
    {
        return new McpValueKey<>("resource_subscription_" + uri, Boolean.class);
    }
}
