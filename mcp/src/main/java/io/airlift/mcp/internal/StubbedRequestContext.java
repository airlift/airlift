package io.airlift.mcp.internal;

import io.airlift.mcp.McpRequestContext;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

class StubbedRequestContext
        implements McpRequestContext
{
    @Override
    public Optional<HttpServletRequest> request()
    {
        return Optional.empty();
    }

    @Override
    public void sendProgress(double progress, double total, String message)
    {
        // NOP
    }

    @Override
    public void sendPing()
    {
        // NOP
    }

    @Override
    public void sendMessage(String method, Optional<Object> params)
    {
        // NOP
    }
}
