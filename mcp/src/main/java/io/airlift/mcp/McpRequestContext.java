package io.airlift.mcp;

import jakarta.servlet.http.HttpServletRequest;

public interface McpRequestContext
{
    HttpServletRequest request();

    void sendProgress(double progress, double total, String message);
}
