package io.airlift.mcp.session;

import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.RootsList;

import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record McpValueKey<T>(String name, Class<T> type)
{
    // session's current logging level
    public static final McpValueKey<LoggingLevel> LOGGING_LEVEL = new McpValueKey<>(LoggingLevel.class.getName(), LoggingLevel.class);

    // client info/capabilities provided during initialize request
    public static final McpValueKey<ClientCapabilities> CLIENT_CAPABILITIES = new McpValueKey<>(ClientCapabilities.class.getName(), ClientCapabilities.class);
    public static final McpValueKey<Implementation> CLIENT_INFO = new McpValueKey<>(Implementation.class.getName() + ".client", Implementation.class);

    // current roots for the session - only set if server has requested the roots list
    public static final McpValueKey<RootsList> ROOTS = new McpValueKey<>(RootsList.class.getName(), RootsList.class);

    // resource subscriptions
    public static final McpValueKey<UUID> RESOURCE_SUBSCRIPTION = new McpValueKey<>("resource_subscription_", UUID.class);
    public static final McpValueKey<UUID> RESOURCE_VERSION = new McpValueKey<>("resource_version_", UUID.class);

    // server-to-client responses
    public static final McpValueKey<JsonRpcMessage> RESPONSE = new McpValueKey<>(JsonRpcResponse.class.getName(), JsonRpcMessage.class);

    // tools, prompts, resources list change management
    public static final McpValueKey<UUID> TOOLS_LIST_VERSION = new McpValueKey<>("tools_list_version", UUID.class);
    public static final McpValueKey<UUID> PROMPTS_LIST_VERSION = new McpValueKey<>("prompts_list_version", UUID.class);
    public static final McpValueKey<UUID> RESOURCES_LIST_VERSION = new McpValueKey<>("resources_list_version", UUID.class);

    public McpValueKey
    {
        requireNonNull(name, "name is null");
        requireNonNull(type, "type is null");
    }

    public McpValueKey<T> withSuffix(String suffix)
    {
        return new McpValueKey<>(name + "." + suffix, type);
    }

    public static boolean isSuffixedKey(McpValueKey<?> key, String name)
    {
        return name.startsWith(key.name() + ".");
    }

    public static String keySuffix(McpValueKey<?> key, String name)
    {
        checkArgument(isSuffixedKey(key, name), "key suffix is not suffixed");

        return name.substring(key.name().length() + 1);
    }
}
