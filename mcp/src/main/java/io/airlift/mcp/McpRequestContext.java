package io.airlift.mcp;

import io.airlift.mcp.model.LoggingLevel;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface McpRequestContext
{
    HttpServletRequest request();

    void sendProgress(double progress, double total, String message);

    void sendPing();

    default void sendLog(LoggingLevel level, String message)
    {
        sendLog(level, Optional.empty(), Optional.of(message));
    }

    default void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data)
    {
        // only implemented when sessions are configured

        throw new UnsupportedOperationException();
    }
}
