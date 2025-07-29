package io.airlift.mcp;

import io.airlift.jsonrpc.model.JsonRpcRequest;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.session.SessionMetadata;

import java.util.Optional;
import java.util.function.Consumer;

public interface McpNotifier
{
    void notifyProgress(String message, Optional<Double> progress, Optional<Double> total);

    <T> void sendNotification(String notificationType, T data);

    void sendNotification(String notificationType);

    /**
     * Note: sendRequest() requires that session support is enabled in the MCP server
     * via {@link McpModule.Builder#withSessionHandling(SessionMetadata, Consumer)}
     * in order to get a response from a request. If session support is not enabled,
     * the request will still be sent but no responses will be processed.
     */
    <T> void sendRequest(JsonRpcRequest<T> request);

    /**
     * Note: sendLog() requires that session support is enabled in the MCP server
     * via {@link McpModule.Builder#withSessionHandling(SessionMetadata, Consumer)}.
     * If session support is not enabled, this method does nothing. The server
     * will check the currently set logging level for the session or the default
     * before sending the log message.
     */
    <T> void sendLog(LoggingLevel level, String logger, T data);

    /**
     * Note: sendLog() requires that session support is enabled in the MCP server
     * via {@link McpModule.Builder#withSessionHandling(SessionMetadata, Consumer)}
     * If session support is not enabled, this method does nothing. The server
     * will check the currently set logging level for the session or the default
     * before sending the log message.
     */
    void sendLog(LoggingLevel level, String logger);

    /**
     * Returns {@code true} if the client has requested cancellation of the
     * current request. Your operation should clean up and exit as soon as possible.
     * If session support is not enabled, this method always returns {@code false}.
     */
    boolean cancellationRequested();
}
