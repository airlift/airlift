package io.airlift.mcp;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.legacy.sessions.LegacySession;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.model.Root;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public interface McpRequestContext
{
    HttpServletRequest request();

    HttpServletResponse response();

    McpRequestContext withSession(LegacySession session);

    Authenticated<?> identity();

    McpRequestContext withProgressToken(Optional<Object> progressToken);

    Protocol protocol();

    void sendProgress(double progress, double total, String message);

    void sendPing();

    void sendMessage(String method, Optional<Object> params);

    default void sendLog(LoggingLevel level, String message)
    {
        sendLog(level, Optional.empty(), Optional.of(message));
    }

    void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data);

    ClientCapabilities clientCapabilities();

    /**
     * Sends a server-to-client request and waits for the response until given timeout.
     */
    <R> JsonRpcResponse<R> serverToClientRequest(String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;

    List<Root> requestRoots(Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;
}
