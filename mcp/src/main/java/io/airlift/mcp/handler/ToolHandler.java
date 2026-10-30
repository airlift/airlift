package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.ToolResult;

public interface ToolHandler
{
    ToolResult callTool(McpRequestContext requestContext, CallToolRequest toolRequest);
}
