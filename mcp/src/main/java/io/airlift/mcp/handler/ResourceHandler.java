package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;

import java.util.List;

public interface ResourceHandler
{
    List<ResourceContents> readResource(Request request, SessionId sessionId, McpNotifier notifier, Resource sourceResource, ReadResourceRequest readResourceRequest);
}
