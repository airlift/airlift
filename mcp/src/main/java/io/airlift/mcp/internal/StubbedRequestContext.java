package io.airlift.mcp.internal;

import com.google.common.collect.ImmutableList;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.Root;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.List;
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
    public List<Root> requestRoots(Duration timeout, Duration pollInterval)
    {
        return ImmutableList.of();
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
