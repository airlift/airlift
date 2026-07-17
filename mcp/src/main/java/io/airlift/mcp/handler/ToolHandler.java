package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.Result;

public interface ToolHandler
{
    Result callTool(McpRequestContext requestContext, CallToolRequest toolRequest);
}
