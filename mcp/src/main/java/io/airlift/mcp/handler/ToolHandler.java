package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;

public interface ToolHandler
{
    CallToolResult callTool(Request request, SessionId sessionId, McpNotifier notifier, CallToolRequest toolRequest);
}
