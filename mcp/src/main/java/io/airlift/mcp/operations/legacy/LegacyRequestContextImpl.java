package io.airlift.mcp.operations.legacy;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.messages.MessageWriter;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.LoggingMessageNotification;
import io.airlift.mcp.model.ProgressNotification;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.operations.legacy.sessions.BlockingResult;
import io.airlift.mcp.operations.legacy.sessions.BlockingResult.Fulfilled;
import io.airlift.mcp.operations.legacy.sessions.Session;
import io.airlift.mcp.operations.legacy.sessions.SessionController;
import io.airlift.mcp.operations.legacy.sessions.SessionId;
import io.airlift.mcp.operations.legacy.sessions.SessionValueKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static io.airlift.mcp.model.JsonRpcRequest.buildRequest;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.operations.legacy.sessions.SessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.operations.legacy.sessions.SessionValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.operations.legacy.sessions.SessionValueKey.PROTOCOL;
import static io.airlift.mcp.operations.legacy.sessions.SessionValueKey.serverToClientResponseKey;
import static java.util.Objects.requireNonNull;

class LegacyRequestContextImpl
        implements McpRequestContext
{
    private final JsonMapper jsonMapper;
    private final Optional<SessionController> sessionController;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final MessageWriter messageWriter;
    private final Optional<Object> progressToken;
    private final Supplier<LoggingLevel> loggingLevelSupplier;
    private final Session session;
    private final Authenticated<?> identity;

    LegacyRequestContextImpl(
            JsonMapper jsonMapper,
            Optional<SessionController> sessionController,
            HttpServletRequest request,
            HttpServletResponse response,
            MessageWriter messageWriter,
            Authenticated<?> identity)
    {
        this(jsonMapper,
                sessionController,
                request,
                response,
                messageWriter,
                Optional.empty(),
                buildLoggingLevelSupplier(sessionController, request),
                new SessionImpl(sessionController, optionalSessionId(request).orElse(Session.NULL_SESSION_ID)),
                identity);
    }

    private static Supplier<LoggingLevel> buildLoggingLevelSupplier(Optional<SessionController> sessionController, HttpServletRequest request)
    {
        return Suppliers.memoize(() -> {
            SessionController localSessionController = sessionController.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));
            SessionId sessionId = requireSessionId(request);

            return localSessionController.getSessionValue(sessionId, LOGGING_LEVEL).orElseThrow(() -> exception("Session is invalid"));
        });
    }

    private LegacyRequestContextImpl(
            JsonMapper jsonMapper,
            Optional<SessionController> sessionController,
            HttpServletRequest request,
            HttpServletResponse response,
            MessageWriter messageWriter,
            Optional<Object> progressToken,
            Supplier<LoggingLevel> loggingLevelSupplier,
            Session session,
            Authenticated<?> identity)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.request = requireNonNull(request, "request is null");
        this.response = requireNonNull(response, "response is null");
        this.messageWriter = requireNonNull(messageWriter, "messageWriter is null");
        this.progressToken = requireNonNull(progressToken, "progressToken is null");
        this.loggingLevelSupplier = requireNonNull(loggingLevelSupplier, "loggingLevelSupplier is null");
        this.session = requireNonNull(session, "session is null");
        this.identity = requireNonNull(identity, "identity is null");
    }

    LegacyRequestContextImpl withProgressToken(Optional<Object> progressToken)
    {
        return new LegacyRequestContextImpl(jsonMapper, sessionController, request, response, messageWriter, progressToken, loggingLevelSupplier, session, identity);
    }

    Protocol protocol()
    {
        return sessionController.flatMap(controller ->
                        optionalSessionId(request).flatMap(sessionId -> controller.getSessionValue(sessionId, PROTOCOL)))
                .orElse(LATEST_PROTOCOL);
    }

    HttpServletResponse response()
    {
        return response;
    }

    LegacyRequestContextImpl withSessionId(SessionId sessionId)
    {
        return new LegacyRequestContextImpl(jsonMapper, sessionController, request, response, messageWriter, progressToken, loggingLevelSupplier, new SessionImpl(sessionController, sessionId), identity);
    }

    @Override
    public Authenticated<?> identity()
    {
        return identity;
    }

    @Override
    public HttpServletRequest request()
    {
        return request;
    }

    Session session()
    {
        return session;
    }

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
    public void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data)
    {
        LoggingLevel sessionLoggingLevel = loggingLevelSupplier.get();
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
        return sessionController.map(controller -> {
            SessionId sessionId = requireSessionId(request);

            // ClientCapabilities are cached via CachingSessionController
            return controller.getSessionValue(sessionId, CLIENT_CAPABILITIES)
                    .orElseThrow(() -> exception("Session does not contain client capabilities"));
        }).orElse(ClientCapabilities.EMPTY);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    <R> JsonRpcResponse<R> serverToClientRequest(String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> new IllegalStateException("Sessions are not enabled"));
        SessionId sessionId = requireSessionId(request);
        String requestId = UUID.randomUUID().toString();

        internalSendRequest(buildRequest(requestId, method, params));
        SessionValueKey<JsonRpcResponse> responseKey = serverToClientResponseKey(requestId);

        while (timeout.isPositive()) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            BlockingResult<JsonRpcResponse> blockingResult = localSessionController.blockUntil(sessionId, responseKey, pollInterval, Optional::isPresent);
            timeout = timeout.minus(stopwatch.elapsed());

            if (blockingResult instanceof Fulfilled<JsonRpcResponse>(var rpcResponse)) {
                try {
                    if (rpcResponse.result().isPresent()) {
                        Object result = rpcResponse.result().orElseThrow();
                        R convertedValue = jsonMapper.convertValue(result, responseType);
                        return new JsonRpcResponse<>(rpcResponse.id(), Optional.empty(), Optional.of(convertedValue));
                    }
                    return rpcResponse;
                }
                finally {
                    localSessionController.deleteSessionValue(sessionId, responseKey);
                }
            }
        }

        throw new TimeoutException("Timed out waiting %s for client to respond".formatted(timeout));
    }

    static SessionId requireSessionId(HttpServletRequest request)
    {
        return optionalSessionId(request).orElseThrow(() -> exception("Missing %s header in request".formatted(MCP_SESSION_ID)));
    }

    static Optional<SessionId> optionalSessionId(HttpServletRequest request)
    {
        return Optional.ofNullable(request.getHeader(MCP_SESSION_ID))
                .map(SessionId::new);
    }

    private void internalSendRequest(JsonRpcRequest<?> rpcRequest)
    {
        try {
            String json = jsonMapper.writeValueAsString(rpcRequest);
            messageWriter.writeMessage(json);
            messageWriter.flush();
        }
        catch (IOException e) {
            throw exception(e);
        }
    }
}
