package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;

public interface ListResourcesHandler
{
    ResourcesEntry listResources(Request request, SessionId sessionId, McpNotifier notifier);
}
