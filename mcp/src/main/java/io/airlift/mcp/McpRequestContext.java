package io.airlift.mcp;

import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Root;
import io.airlift.mcp.tasks.Tasks;
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

    // only implemented when sessions are configured
    default void sendLog(LoggingLevel level, String message)
    {
        sendLog(level, Optional.empty(), Optional.of(message));
    }

    // only implemented when sessions are configured
    void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data);

    // only implemented when sessions are configured
    ClientCapabilities clientCapabilities();

    /**
     * Sends a server-to-client request and waits for the response until given timeout.
     * Only implemented when sessions are configured
     */
    <R> JsonRpcResponse<R> serverToClientRequest(String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;

    // only implemented when sessions are configured
    List<Root> requestRoots(Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;

    // only implemented when sessions and tasks are configured
    Tasks tasks();
}
