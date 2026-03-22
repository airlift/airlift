package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.base.Stopwatch;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.MessageWriter;
import io.airlift.mcp.legacy.sessions.LegacyBlockingResult;
import io.airlift.mcp.legacy.sessions.LegacyBlockingResult.Fulfilled;
import io.airlift.mcp.legacy.sessions.LegacySession;
import io.airlift.mcp.legacy.sessions.LegacySessionValueKey;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListRootsResult;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.LoggingMessageNotification;
import io.airlift.mcp.model.ProgressNotification;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.model.Root;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.google.common.base.Throwables.getRootCause;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.PROTOCOL;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.ROOTS;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.serverToClientResponseKey;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.METHOD_ROOTS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static io.airlift.mcp.model.JsonRpcRequest.buildRequest;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static java.util.Objects.requireNonNull;

class InternalRequestContext
        implements McpRequestContext
{
    private static final Duration PING_THRESHOLD = Duration.ofSeconds(15);

    private final JsonMapper jsonMapper;
    private final Optional<LegacySession> session;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final MessageWriter messageWriter;
    private final Optional<Object> progressToken;
    private final Supplier<LoggingLevel> loggingLevelSupplier;
    private final Authenticated<?> identity;

    InternalRequestContext(
            JsonMapper jsonMapper,
            Optional<LegacySession> session,
            HttpServletRequest request,
            HttpServletResponse response,
            MessageWriter messageWriter,
            Authenticated<?> identity)
    {
        this(jsonMapper,
                session,
                request,
                response,
                messageWriter,
                Optional.empty(),
                buildLoggingLevelSupplier(session),
                identity);
    }

    private static Supplier<LoggingLevel> buildLoggingLevelSupplier(Optional<LegacySession> session)
    {
        return Suppliers.memoize(() -> {
            LegacySession localSession = session.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));
            return localSession.getValue(LOGGING_LEVEL).orElseThrow(() -> exception("Session is invalid"));
        });
    }

    private InternalRequestContext(
            JsonMapper jsonMapper,
            Optional<LegacySession> session,
            HttpServletRequest request,
            HttpServletResponse response,
            MessageWriter messageWriter,
            Optional<Object> progressToken,
            Supplier<LoggingLevel> loggingLevelSupplier,
            Authenticated<?> identity)
    {
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.session = requireNonNull(session, "session is null");
        this.request = requireNonNull(request, "request is null");
        this.response = requireNonNull(response, "request is null");
        this.messageWriter = requireNonNull(messageWriter, "messageWriter is null");
        this.progressToken = requireNonNull(progressToken, "progressToken is null");
        this.loggingLevelSupplier = requireNonNull(loggingLevelSupplier, "loggingLevelSupplier is null");
        this.identity = requireNonNull(identity, "identity is null");
    }

    @Override
    public InternalRequestContext withProgressToken(Optional<Object> progressToken)
    {
        return new InternalRequestContext(jsonMapper, session, request, response, messageWriter, progressToken, loggingLevelSupplier, identity);
    }

    @Override
    public Protocol protocol()
    {
        return session.flatMap(localSession -> localSession.getValue(PROTOCOL))
                .orElse(LATEST_PROTOCOL);
    }

    @Override
    public HttpServletResponse response()
    {
        return response;
    }

    @Override
    public InternalRequestContext withSession(LegacySession session)
    {
        return new InternalRequestContext(jsonMapper, Optional.of(session), request, response, messageWriter, progressToken, loggingLevelSupplier, identity);
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
        LegacySession localSession = session.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));

        return localSession.getValue(CLIENT_CAPABILITIES)
                .orElseThrow(() -> exception("Session does not contain client capabilities"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <R> JsonRpcResponse<R> serverToClientRequest(String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException
    {
        LegacySession localSession = session.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));
        String requestId = UUID.randomUUID().toString();

        internalSendRequest(buildRequest(requestId, method, params));
        LegacySessionValueKey<JsonRpcResponse> responseKey = serverToClientResponseKey(requestId);

        Stopwatch pingStopwatch = Stopwatch.createStarted();

        while (timeout.isPositive()) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            LegacyBlockingResult<JsonRpcResponse> blockingResult = localSession.blockUntil(responseKey, pollInterval, Optional::isPresent);
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
                    localSession.deleteValue(responseKey);
                }
            }

            if (pingStopwatch.elapsed().compareTo(PING_THRESHOLD) >= 0) {
                sendPing();
                pingStopwatch.reset().start();
            }
        }

        throw new TimeoutException("Timed out waiting %s for client to respond".formatted(timeout));
    }

    @SuppressWarnings("ThrowableNotThrown")
    @Override
    public List<Root> requestRoots(Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException
    {
        LegacySession localSession = session.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));

        Optional<List<Root>> maybeRoots = localSession.getValue(ROOTS)
                .map(ListRootsResult::roots);

        try {
            return maybeRoots.or(() -> localSession.getValue(CLIENT_CAPABILITIES).map(clientCapabilities -> {
                if (clientCapabilities.roots().isPresent()) {
                    try {
                        return updateRoots(localSession, timeout, pollInterval);
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    catch (TimeoutException e) {
                        throw new RuntimeException(e);
                    }
                }
                return ImmutableList.of();
            })).orElseGet(ImmutableList::of);
        }
        catch (Exception e) {
            switch (getRootCause(e)) {
                case TimeoutException timeoutException -> throw timeoutException;
                case InterruptedException interruptedException -> throw interruptedException;
                case RuntimeException runtimeException -> throw runtimeException;
                case Throwable throwable -> throw new RuntimeException(throwable);
            }
        }
    }

    private List<Root> updateRoots(LegacySession session, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException
    {
        JsonRpcResponse<ListRootsResult> newRoots = serverToClientRequest(METHOD_ROOTS_LIST, ImmutableMap.of(), ListRootsResult.class, timeout, pollInterval);

        if (newRoots.error().isPresent()) {
            JsonRpcErrorDetail error = newRoots.error().orElseThrow();
            throw exception(error.code(), error.message());
        }

        if (newRoots.result().isPresent()) {
            ListRootsResult listRootsResult = newRoots.result().orElseThrow();
            session.setValue(ROOTS, listRootsResult);
            return listRootsResult.roots();
        }

        return ImmutableList.of();
    }

    private void internalSendRequest(JsonRpcRequest<?> rpcRequest)
    {
        try {
            String json = jsonMapper.writeValueAsString(rpcRequest);
            messageWriter.writeMessage(json);
            messageWriter.flushMessages();
        }
        catch (IOException e) {
            throw exception(e);
        }
    }
}
