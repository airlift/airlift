package io.airlift.mcp;

import io.airlift.mcp.model.McpIdentity;
import jakarta.servlet.http.HttpServletRequest;

public interface McpIdentityMapper
{
    /**
     * Map the request to an McpIdentity
     */
    McpIdentity map(HttpServletRequest request);
}
