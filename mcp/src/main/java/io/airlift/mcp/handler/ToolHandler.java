package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;

public interface ToolHandler
{
    CallToolResult callTool(McpRequestContext requestContext, CallToolRequest toolRequest);
}
