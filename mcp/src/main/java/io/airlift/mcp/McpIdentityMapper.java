package io.airlift.mcp;

import io.airlift.mcp.model.McpIdentity;
import jakarta.servlet.http.HttpServletRequest;

public interface McpIdentityMapper
{
    McpIdentity map(HttpServletRequest request);
}
