package io.airlift.mcp.handler;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResponse;

public interface ToolHandler
{
    CallToolResponse callTool(McpRequestContext requestContext, CallToolRequest toolRequest);
}
