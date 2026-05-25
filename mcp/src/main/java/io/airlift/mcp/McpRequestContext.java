package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Task;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface McpRequestContext
{
    HttpServletRequest request();

    Authenticated<?> identity();

    void sendProgress(double progress, double total, String message);

    void sendMessage(String method, Optional<Object> params);

    ClientCapabilities clientCapabilities();

    default void sendLog(LoggingLevel level, String message)
    {
        sendLog(level, Optional.empty(), Optional.of(message));
    }

    void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data);

    Task createTask();
}
