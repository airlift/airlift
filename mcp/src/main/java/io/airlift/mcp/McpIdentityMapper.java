package io.airlift.mcp;

import jakarta.servlet.http.HttpServletRequest;

public interface McpIdentityMapper<T>
{
    T map(HttpServletRequest request);
}
