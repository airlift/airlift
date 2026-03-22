package io.airlift.mcp.features;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.legacy.LegacyEventStreaming;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.SetLevelRequest;
import io.airlift.mcp.model.SubscribeRequest;

public interface Features
        extends LegacyEventStreaming
{
    boolean sessionEnabled();

    InitializeResult initialize(McpRequestContext requestContext, InitializeRequest initializeRequest);

    ListToolsResult listTools(McpRequestContext requestContext, ListRequest listRequest);

    ListPromptsResult listPrompts(McpRequestContext requestContext, ListRequest listRequest);

    ListResourcesResult listResources(McpRequestContext requestContext, ListRequest listRequest);

    ListResourceTemplatesResult listResourceTemplates(McpRequestContext requestContext, ListRequest listRequest);

    CallToolResult callTool(McpRequestContext requestContext, CallToolRequest callToolRequest);

    GetPromptResult getPrompt(McpRequestContext requestContext, GetPromptRequest getPromptRequest);

    ReadResourceResult readResources(McpRequestContext requestContext, ReadResourceRequest readResourceRequest);

    void setLoggingLevel(McpRequestContext requestContext, SetLevelRequest setLevelRequest);

    CompleteResult completionComplete(McpRequestContext requestContext, CompleteRequest completeRequest);

    void resourcesSubscribe(McpRequestContext requestContext, SubscribeRequest subscribeRequest);

    void resourcesUnsubscribe(McpRequestContext requestContext, SubscribeRequest subscribeRequest);

    void reconcileVersions(McpRequestContext requestContext);

    void acceptCancellation(McpRequestContext requestContext, CancelledNotification cancelledNotification);

    void acceptRootsChanged(McpRequestContext requestContext);

    void acceptResponse(McpRequestContext requestContext, JsonRpcResponse<?> rpcResponse);

    void acceptSessionDelete(McpRequestContext requestContext);
}
