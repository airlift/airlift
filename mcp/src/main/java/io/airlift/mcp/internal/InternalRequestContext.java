package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.MessageWriter;
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
import io.airlift.mcp.sessions.BlockingResult;
import io.airlift.mcp.sessions.BlockingResult.Fulfilled;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import io.airlift.mcp.tasks.TaskContextId;
import io.airlift.mcp.tasks.TaskController;
import io.airlift.mcp.tasks.Tasks;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Throwables.getRootCause;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.METHOD_ROOTS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.Constants.RPC_REQUEST_ID_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.TASK_CONTEXT_ID_ATTRIBUTE;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static io.airlift.mcp.model.JsonRpcRequest.buildRequest;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.sessions.SessionValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.sessions.SessionValueKey.PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.ROOTS;
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
    private final Supplier<Tasks> tasksSupplier;

    InternalRequestContext(ObjectMapper objectMapper,
            Optional<SessionController> sessionController,
            HttpServletRequest request,
            MessageWriter messageWriter,
            Optional<Object> progressToken,
            Optional<TaskController> taskController)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.request = requireNonNull(request, "request is null");
        this.messageWriter = requireNonNull(messageWriter, "messageWriter is null");
        this.progressToken = requireNonNull(progressToken, "progressToken is null");

        tasksSupplier = Suppliers.memoize(() -> {
            TaskController localTaskController = taskController.orElseThrow(() -> new IllegalStateException("Tasks not enabled"));
            TaskContextId taskContextId = (TaskContextId) Optional.ofNullable(request.getAttribute(TASK_CONTEXT_ID_ATTRIBUTE)).orElseThrow(() -> exception("task context id not set in request"));
            Object requestId = Optional.ofNullable(request.getAttribute(RPC_REQUEST_ID_ATTRIBUTE)).orElseThrow(() -> exception("request id not set in request"));

            return localTaskController.tasksForRequest(taskContextId, requestId, progressToken);
        });
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

    @Override
    public ClientCapabilities clientCapabilities()
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));
        SessionId sessionId = requireSessionId(request);

        return localSessionController.getSessionValue(sessionId, CLIENT_CAPABILITIES)
                .orElseThrow(() -> exception("Session does not contain client capabilities"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public <R> JsonRpcResponse<R> serverToClientRequest(String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> new IllegalStateException("Sessions are not enabled"));
        SessionId sessionId = requireSessionId(request);
        String requestId = UUID.randomUUID().toString();

        internalSendRequest(buildRequest(requestId, method, params));
        SessionValueKey<JsonRpcResponse> responseKey = serverToClientResponseKey(requestId);

        Stopwatch pingStopwatch = Stopwatch.createStarted();

        while (timeout.isPositive()) {
            Stopwatch stopwatch = Stopwatch.createStarted();
            BlockingResult<JsonRpcResponse> blockingResult = localSessionController.blockUntil(sessionId, responseKey, pollInterval, Optional::isPresent);
            timeout = timeout.minus(stopwatch.elapsed());

            if (blockingResult instanceof Fulfilled<JsonRpcResponse>(var rpcResponse)) {
                try {
                    if (rpcResponse.result().isPresent()) {
                        Object result = rpcResponse.result().orElseThrow();
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

        throw new TimeoutException("Timed out waiting %s for client to respond".formatted(timeout));
    }

    @SuppressWarnings("ThrowableNotThrown")
    @Override
    public List<Root> requestRoots(Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> new IllegalStateException("Sessions not enabled"));
        SessionId sessionId = requireSessionId(request);

        Optional<List<Root>> maybeRoots = localSessionController.getSessionValue(sessionId, ROOTS)
                .map(ListRootsResult::roots);

        try {
            return maybeRoots.or(() -> localSessionController.getSessionValue(sessionId, CLIENT_CAPABILITIES).map(clientCapabilities -> {
                if (clientCapabilities.roots().isPresent()) {
                    try {
                        return updateRoots(timeout, pollInterval, sessionId);
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

    @Override
    public Tasks tasks()
    {
        return tasksSupplier.get();
    }

    static SessionId requireSessionId(Optional<SessionId> maybeSessionId)
    {
        return maybeSessionId.orElseThrow(() -> exception("Missing %s header in request".formatted(MCP_SESSION_ID)));
    }

    static SessionId requireSessionId(HttpServletRequest request)
    {
        return requireSessionId(optionalSessionId(request));
    }

    static Optional<SessionId> optionalSessionId(HttpServletRequest request)
    {
        return Optional.ofNullable(request.getHeader(MCP_SESSION_ID))
                .map(SessionId::new);
    }

    static Protocol protocol(Optional<SessionController> sessionController, HttpServletRequest request)
    {
        return sessionController.flatMap(controller ->
                        optionalSessionId(request).flatMap(sessionId -> controller.getSessionValue(sessionId, PROTOCOL)))
                .orElse(LATEST_PROTOCOL);
    }

    private List<Root> updateRoots(Duration timeout, Duration pollInterval, SessionId sessionId)
            throws InterruptedException, TimeoutException
    {
        JsonRpcResponse<ListRootsResult> newRoots = serverToClientRequest(METHOD_ROOTS_LIST, ImmutableMap.of(), ListRootsResult.class, timeout, pollInterval);

        if (newRoots.error().isPresent()) {
            JsonRpcErrorDetail error = newRoots.error().orElseThrow();
            throw exception(error.code(), error.message());
        }

        if (newRoots.result().isPresent()) {
            ListRootsResult listRootsResult = newRoots.result().orElseThrow();
            sessionController.ifPresent(controller -> controller.setSessionValue(sessionId, ROOTS, listRootsResult));
            return listRootsResult.roots();
        }

        return ImmutableList.of();
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
