package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.MessageWriter;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.LoggingMessageNotification;
import io.airlift.mcp.model.ProgressNotification;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static io.airlift.mcp.sessions.SessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.sessions.SessionValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.sessions.SessionValueKey.serverToClientResponseKey;
import static java.util.Objects.requireNonNull;

class InternalRequestContext
        implements McpRequestContext
{
    private static final Duration PING_THRESHOLD = Duration.ofSeconds(15);

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
    public Optional<HttpServletRequest> request()
    {
        return Optional.of(request);
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

    @Override
    public ClientCapabilities clientCapabilities()
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));
        SessionId sessionId = requireSessionId(request);

        return localSessionController.getSessionValue(sessionId, CLIENT_CAPABILITIES)
                .orElseThrow(() -> exception("Session does not contain client capabilities"));
    }

    @SuppressWarnings({"rawtypes", "BusyWait", "unchecked"})
    @Override
    public <R> JsonRpcResponse<R> serverToClientRequest(String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));
        SessionId sessionId = requireSessionId(request);
        String requestId = UUID.randomUUID().toString();

        JsonRpcRequest<?> jsonRpcRequest = JsonRpcRequest.buildRequest(requestId, method, params);
        internalSendRequest(jsonRpcRequest);

        SessionValueKey<JsonRpcResponse> responseKey = serverToClientResponseKey(requestId);

        Stopwatch pingStopwatch = Stopwatch.createStarted();

        while (timeout.isPositive()) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            try {
                Thread.sleep(pollInterval.toMillis());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
            timeout = timeout.minus(stopwatch.elapsed());

            Optional<JsonRpcResponse> maybeResponse = localSessionController.getSessionValue(sessionId, responseKey);
            if (maybeResponse.isPresent()) {
                try {
                    JsonRpcResponse rpcResponse = maybeResponse.get();
                    if (rpcResponse.result().isPresent()) {
                        Object result = rpcResponse.result().get();
                        R convertedValue = objectMapper.convertValue(result, responseType);
                        return new JsonRpcResponse<>(rpcResponse.id(), Optional.empty(), Optional.of(convertedValue));
                    }
                    return rpcResponse;
                }
                finally {
                    localSessionController.deleteSessionValue(sessionId, responseKey);
                }
            }

            if (pingStopwatch.elapsed().compareTo(PING_THRESHOLD) >= 0) {
                sendPing();
                pingStopwatch.reset().start();
            }
        }

        throw new TimeoutException("Timed out waiting for server to client response");
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
