package io.airlift.mcp;

import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Root;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public interface McpRequestContext
{
    HttpServletRequest request();

    void sendProgress(double progress, double total, String message);

    void sendPing();

    void sendMessage(String method, Optional<Object> params);

    default void sendLog(LoggingLevel level, String message)
    {
        sendLog(level, Optional.empty(), Optional.of(message));
    }

    default void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data)
    {
        // only implemented when sessions are configured

        throw new UnsupportedOperationException();
    }

    default ClientCapabilities clientCapabilities()
    {
        // only implemented when sessions are configured

        throw new UnsupportedOperationException();
    }

    /**
     * Sends a server-to-client request and waits for the response until given timeout.
     */
    <R> JsonRpcResponse<R> serverToClientRequest(String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;

    List<Root> requestRoots(Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;
}
