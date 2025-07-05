package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import jakarta.ws.rs.core.Request;

public interface ToolHandler
{
    CallToolResult callTool(Request request, McpNotifier notifier, CallToolRequest toolRequest);
}
