package io.airlift.mcp.handler;

import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;

public interface ToolHandler
{
    CallToolResult callTool(RequestContext requestContext, McpNotifier notifier, CallToolRequest toolRequest);
}
