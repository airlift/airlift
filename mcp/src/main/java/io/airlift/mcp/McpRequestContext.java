package io.airlift.mcp;

import jakarta.servlet.http.HttpServletRequest;

public interface McpRequestContext
{
    HttpServletRequest request();

    void ping();

    void sendProgress(double progress, double total, String message);
}
