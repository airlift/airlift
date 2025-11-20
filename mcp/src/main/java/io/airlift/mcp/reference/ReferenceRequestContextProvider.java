package io.airlift.mcp.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.handler.RequestContextProvider;
import io.airlift.mcp.session.McpSessionController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class ReferenceRequestContextProvider
        implements RequestContextProvider
{
    private final ObjectMapper objectMapper;
    private final Optional<McpSessionController> sessionController;
    private final Provider<McpServer> server;

    @Inject
    public ReferenceRequestContextProvider(ObjectMapper objectMapper, Optional<McpSessionController> sessionController, Provider<McpServer> server)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.server = requireNonNull(server, "server is null");
    }

    @Override
    public McpRequestContext get(HttpServletRequest request, HttpServletResponse response, Optional<Object> progressToken)
    {
        return new ReferenceRequestContext(request, response, server.get(), sessionController, progressToken, objectMapper);
    }
}
