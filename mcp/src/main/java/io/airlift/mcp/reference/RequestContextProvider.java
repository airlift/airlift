package io.airlift.mcp.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
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
import static io.airlift.mcp.model.JsonRpcRequest.JSON_RPC_VERSION;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static io.airlift.mcp.reference.ReferenceServerTransport.requireSessionId;
import static io.airlift.mcp.sessions.SessionKey.LOGGING_LEVEL;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_MESSAGE;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_PROGRESS;
import static java.util.Objects.requireNonNull;

public class RequestContextProvider
{
    private final ObjectMapper objectMapper;
    private final Optional<SessionController> sessionController;

    @Inject
    public RequestContextProvider(ObjectMapper objectMapper, Optional<SessionController> sessionController)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
    }

    public McpRequestContext build(HttpServletRequest request, MessageWriter messageWriter, Optional<Object> progressToken)
    {
        return new McpRequestContext()
        {
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
                    case Number number -> Optional.of(number.longValue());
                    default -> progressToken;
                });

                ProgressNotification notification = new ProgressNotification(appliedProgressToken, message, OptionalDouble.of(progress), OptionalDouble.of(total));
                internalSendMessage(METHOD_NOTIFICATION_PROGRESS, Optional.of(notification));
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
                    internalSendMessage(METHOD_NOTIFICATION_MESSAGE, Optional.of(logNotification));
                }
            }

            @Override
            public <T> void sendMessage(Optional<Object> id, String method, Optional<T> params)
            {
                JsonRpcRequest<?> message = new JsonRpcRequest<>(JSON_RPC_VERSION, id.orElse(null), method, params);
                internalSendRequest(message);
            }

            private void internalSendMessage(String method, Optional<Object> params)
            {
                JsonRpcRequest<?> notification = params.map(param -> buildNotification(method, param)).orElseGet(() -> buildNotification(method));
                internalSendRequest(notification);
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
        };
    }
}
