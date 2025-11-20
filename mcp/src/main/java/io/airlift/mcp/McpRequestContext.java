package io.airlift.mcp;

import io.airlift.mcp.model.LoggingLevel;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface McpRequestContext
{
    HttpServletRequest request();

    void sendProgress(double progress, double total, String message);

    <T> void sendMessage(Optional<Object> id, String method, Optional<T> params);

    default <T> void sendNotification(String method, Optional<T> params)
    {
        sendMessage(Optional.empty(), method, params);
    }

    default <T> void sendRequest(Object id, String method, Optional<T> params)
    {
        sendMessage(Optional.of(id), method, params);
    }

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
