package io.airlift.mcp.client;

import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.DiscoverResult;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.SubscriptionNotifications;

import java.io.Closeable;
import java.net.URI;
import java.util.Optional;

public interface McpConnection
        extends Closeable
{
    @Override
    void close();

    URI uri();

    <V> V setting(McpConnectionSetting<V> setting);

    <V> McpConnection withSetting(McpConnectionSetting<V> setting, V value);

    DiscoverResult serverDiscover();

    ListToolsResult listTools(Optional<String> cursor);

    default ListToolsResult listTools()
    {
        return listTools(Optional.empty());
    }

    ListPromptsResult listPrompts(Optional<String> cursor);

    default ListPromptsResult listPrompts()
    {
        return listPrompts(Optional.empty());
    }

    ListResourcesResult listResources(Optional<String> cursor);

    default ListResourcesResult listResources()
    {
        return listResources(Optional.empty());
    }

    ListResourceTemplatesResult listResourceTemplates(Optional<String> cursor);

    default ListResourceTemplatesResult listResourceTemplates()
    {
        return listResourceTemplates(Optional.empty());
    }

    CallToolResult callTool(CallToolRequest callToolRequest);

    GetPromptResult getPrompt(GetPromptRequest getPromptRequest);

    ReadResourceResult readResource(ReadResourceRequest readResourceRequest);

    CompleteResult completeCompletion(CompleteRequest completeRequest);

    AutoCloseable subscribe(SubscriptionNotifications subscriptionNotifications);
}
