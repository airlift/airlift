package io.airlift.mcp;

import jakarta.servlet.http.HttpServletRequest;

public interface McpIdentitySupplier<T>
{
    T get(HttpServletRequest request);
}
