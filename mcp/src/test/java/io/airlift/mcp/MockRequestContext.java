package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Task;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

class MockRequestContext
        implements McpRequestContext
{
    private final Authenticated<?> authenticated;
    private final ClientCapabilities clientCapabilities;

    MockRequestContext(Authenticated<?> authenticated, ClientCapabilities clientCapabilities)
    {
        this.authenticated = requireNonNull(authenticated, "authenticated is null");
        this.clientCapabilities = requireNonNull(clientCapabilities, "clientCapabilities is null");
    }

    @Override
    public HttpServletRequest request()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Authenticated<?> identity()
    {
        return authenticated;
    }

    @Override
    public Task createTask()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendProgress(double progress, double total, String message)
    {
        // NOP
    }

    @Override
    public void sendMessage(String method, Optional<Object> params)
    {
        // NOP
    }

    @Override
    public ClientCapabilities clientCapabilities()
    {
        return clientCapabilities;
    }

    @Override
    public void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data)
    {
        // NOP
    }
}
