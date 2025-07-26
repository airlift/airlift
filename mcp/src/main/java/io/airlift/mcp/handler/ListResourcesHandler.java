package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import jakarta.ws.rs.core.Request;

public interface ListResourcesHandler
{
    ResourcesEntry listResources(Request request, McpNotifier notifier);
}
