package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.MessageWriter;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.LoggingMessageNotification;
import io.airlift.mcp.model.ProgressNotification;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalDouble;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static io.airlift.mcp.sessions.SessionValueKey.LOGGING_LEVEL;
import static java.util.Objects.requireNonNull;

class InternalRequestContext
        implements McpRequestContext
{
    private final ObjectMapper objectMapper;
    private final Optional<SessionController> sessionController;
    private final HttpServletRequest request;
    private final MessageWriter messageWriter;
    private final Optional<Object> progressToken;

    InternalRequestContext(ObjectMapper objectMapper, Optional<SessionController> sessionController, HttpServletRequest request, MessageWriter messageWriter, Optional<Object> progressToken)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.request = requireNonNull(request, "request is null");
        this.messageWriter = requireNonNull(messageWriter, "messageWriter is null");
        this.progressToken = requireNonNull(progressToken, "progressToken is null");
    }

    @Override
    public HttpServletRequest request()
    {
        return request;
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    @Override
    public void sendProgress(double progress, double total, String message)
    {
        Optional<Object> appliedProgressToken = progressToken.map(token -> switch (token) {
            case Number n -> Optional.of(n.longValue());
            default -> progressToken;
        });

        ProgressNotification notification = new ProgressNotification(appliedProgressToken, message, OptionalDouble.of(progress), OptionalDouble.of(total));
        sendMessage(NOTIFICATION_PROGRESS, Optional.of(notification));
    }

    @Override
    public void sendPing()
    {
        sendMessage(METHOD_PING, Optional.empty());
    }

    @Override
    public void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data)
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));
        SessionId sessionId = requireSessionId(request);

        LoggingLevel sessionLoggingLevel = localSessionController.getSessionValue(sessionId, LOGGING_LEVEL)
                .orElseThrow(() -> exception("Session is invalid"));
        if (level.level() >= sessionLoggingLevel.level()) {
            LoggingMessageNotification logNotification = new LoggingMessageNotification(level, logger, data);
            sendMessage(NOTIFICATION_MESSAGE, Optional.of(logNotification));
        }
    }

    @Override
    public void sendMessage(String method, Optional<Object> params)
    {
        JsonRpcRequest<?> notification = params.map(p -> buildNotification(method, p)).orElseGet(() -> buildNotification(method));
        internalSendRequest(notification);
    }

    static SessionId requireSessionId(HttpServletRequest request)
    {
        return optionalSessionId(request).orElseThrow(() -> exception("Missing MCP_SESSION_ID header in request"));
    }

    static Optional<SessionId> optionalSessionId(HttpServletRequest request)
    {
        return Optional.ofNullable(request.getHeader(MCP_SESSION_ID))
                .map(SessionId::new);
    }

    private void internalSendRequest(JsonRpcRequest<?> rpcRequest)
    {
        try {
            String json = objectMapper.writeValueAsString(rpcRequest);
            messageWriter.writeMessage(json);
            messageWriter.flushMessages();
        }
        catch (IOException e) {
            throw exception(e);
        }
    }
}
