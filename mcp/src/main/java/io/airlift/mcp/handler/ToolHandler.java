package io.airlift.mcp.handler;

import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import jakarta.servlet.http.HttpServletRequest;

public interface ToolHandler {
    CallToolResult callTool(HttpServletRequest request, CallToolRequest toolRequest);
}
