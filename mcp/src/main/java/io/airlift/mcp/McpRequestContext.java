package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
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

    Authenticated<?> identity();

    void sendProgress(double progress, double total, String message);

    void sendPing();

    void sendMessage(String method, Optional<Object> params);

    ClientCapabilities clientCapabilities();

    // NOTE - may not be supported depending on how you've configured MCP - see the README regarding Sessions and Storage
    default void sendLog(LoggingLevel level, String message)
    {
        sendLog(level, Optional.empty(), Optional.of(message));
    }

    // NOTE - may not be supported depending on how you've configured MCP - see the README regarding Sessions and Storage
    void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data);

    /**
     * Sends a server-to-client request and waits for the response until given timeout. NOTE - may not be supported depending on how you've configured MCP - see the README regarding Sessions and Storage
     */
    <R> JsonRpcResponse<R> serverToClientRequest(String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;

    // NOTE - may not be supported depending on how you've configured MCP - see the README regarding Sessions and Storage
    List<Root> requestRoots(Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;
}
