package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.CancellationController;
import io.airlift.mcp.ErrorHandler;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpIdentityMapper;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.SentMessages;
import io.airlift.mcp.SentMessages.SentMessage;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetTaskRequest;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.SetLevelRequest;
import io.airlift.mcp.model.SubscribeRequest;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import io.airlift.mcp.tasks.TaskContextId;
import io.airlift.mcp.tasks.TaskContextMapper;
import io.airlift.mcp.tasks.TaskController;
import io.opentelemetry.api.common.AttributeKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static io.airlift.http.server.tracing.TracingServletFilter.updateRequestSpan;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.internal.InternalRequestContext.optionalSessionId;
import static io.airlift.mcp.internal.InternalRequestContext.protocol;
import static io.airlift.mcp.internal.InternalRequestContext.requireSessionId;
import static io.airlift.mcp.model.Constants.HEADER_LAST_EVENT_ID;
import static io.airlift.mcp.model.Constants.HEADER_SESSION_ID;
import static io.airlift.mcp.model.Constants.MCP_IDENTITY_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.MESSAGE_WRITER_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.META_RELATED_TASK;
import static io.airlift.mcp.model.Constants.METHOD_CANCEL_TASK;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_GET_TASK;
import static io.airlift.mcp.model.Constants.METHOD_GET_TASK_RESULT;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
import static io.airlift.mcp.model.Constants.METHOD_LIST_TASKS;
import static io.airlift.mcp.model.Constants.METHOD_LOGGING_SET_LEVEL;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_GET;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_SUBSCRIBE;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_UNSUBSCRIBE;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_CALL;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_INITIALIZED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_ROOTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.RPC_MESSAGE_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.RPC_REQUEST_ID_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.TASK_CONTEXT_ID_ATTRIBUTE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.METHOD_NOT_FOUND;
import static io.airlift.mcp.model.JsonRpcErrorCode.PARSE_ERROR;
import static io.airlift.mcp.model.Meta.META_FIELD_NAME;
import static io.airlift.mcp.sessions.SessionValueKey.ROOTS;
import static io.airlift.mcp.sessions.SessionValueKey.SENT_MESSAGES;
import static io.airlift.mcp.sessions.SessionValueKey.cancellationKey;
import static io.airlift.mcp.sessions.SessionValueKey.serverToClientResponseKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static jakarta.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public class InternalFilter
        extends HttpFilter
{
    private static final Logger log = Logger.get(InternalFilter.class);

    private static final Set<String> ALLOWED_HTTP_METHODS = ImmutableSet.of("GET", "POST", "DELETE");

    // TODO - remove when the next rev of opentelemetry-semconv is released
    // see: https://github.com/open-telemetry/semantic-conventions-java/pull/396/changes#diff-1c3840320c0c5bfe484b22e6db177a87b014591107c61f9d5763f92117ddf7ff
    static final AttributeKey<String> MCP_METHOD_NAME = stringKey("mcp.method.name");
    static final AttributeKey<String> MCP_PROTOCOL_VERSION = stringKey("mcp.protocol.version");
    static final AttributeKey<String> MCP_RESOURCE_URI = stringKey("mcp.resource.uri");
    static final AttributeKey<String> MCP_SESSION_ID = stringKey("mcp.session.id");

    private final McpMetadata metadata;
    private final McpIdentityMapper identityMapper;
    private final InternalMcpServer mcpServer;
    private final ObjectMapper objectMapper;
    private final ErrorHandler errorHandler;
    private final Optional<SessionController> sessionController;
    private final Optional<TaskContextMapper> taskContextMapper;
    private final Optional<TaskController> taskController;
    private final boolean httpGetEventsEnabled;
    private final Duration streamingPingThreshold;
    private final Duration streamingTimeout;
    private final CancellationController cancellationController;
    private final int maxResumableMessages;

    @Inject
    public InternalFilter(
            McpMetadata metadata,
            McpIdentityMapper identityMapper,
            InternalMcpServer mcpServer,
            ObjectMapper objectMapper,
            ErrorHandler errorHandler,
            Optional<SessionController> sessionController,
            CancellationController cancellationController,
            Optional<TaskContextMapper> taskContextMapper,
            Optional<TaskController> taskController,
            McpConfig mcpConfig)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.identityMapper = requireNonNull(identityMapper, "identityMapper is null");
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.errorHandler = requireNonNull(errorHandler, "errorHandler is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.cancellationController = requireNonNull(cancellationController, "cancellationController is null");
        this.taskContextMapper = requireNonNull(taskContextMapper, "taskContextMapper is null");
        this.taskController = requireNonNull(taskController, "taskController is null");

        httpGetEventsEnabled = mcpConfig.isHttpGetEventsEnabled();
        streamingPingThreshold = mcpConfig.getEventStreamingPingThreshold().toJavaTime();
        streamingTimeout = mcpConfig.getEventStreamingTimeout().toJavaTime();
        maxResumableMessages = mcpConfig.getMaxResumableMessages();
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        if (!isMcpRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        optionalSessionId(request)
                .ifPresent(sessionId ->
                        updateRequestSpan(request, span -> span.setAttribute(MCP_SESSION_ID, sessionId.id())));

        McpIdentity identity = identityMapper.map(request);
        try {
            switch (identity) {
                case Authenticated<?> authenticated -> handleMpcRequest(request, response, authenticated);

                case McpIdentity.Unauthenticated unauthenticated -> {
                    response.setContentType(APPLICATION_JSON);
                    response.sendError(SC_UNAUTHORIZED, unauthenticated.message());
                    unauthenticated.authenticateHeaders()
                            .forEach(header -> response.addHeader(WWW_AUTHENTICATE, header));
                }

                case McpIdentity.Unauthorized unauthorized -> {
                    response.setContentType(APPLICATION_JSON);
                    response.sendError(SC_FORBIDDEN, unauthorized.message());
                }

                case McpIdentity.Error error -> throw error.cause();
            }
        }
        catch (Throwable throwable) {
            errorHandler.handleException(request, response, throwable);
        }
    }

    private void handleMpcRequest(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated)
            throws Exception
    {
        request.setAttribute(MCP_IDENTITY_ATTRIBUTE, authenticated.identity());

        switch (request.getMethod().toUpperCase(ROOT)) {
            case POST -> handleMcpPostRequest(request, response, authenticated);
            case GET -> handleMpcGetRequest(request, response, authenticated);
            case DELETE -> handleMcpDeleteRequest(request, response);
            default -> response.setStatus(SC_NOT_FOUND);
        }
    }

    private void handleMpcGetRequest(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated)
    {
        boolean wasHandled = httpGetEventsEnabled && sessionController.map(controller -> {
            handleEventStreaming(request, response, authenticated, controller);
            return true;
        }).orElse(false);

        if (!wasHandled) {
            response.setStatus(SC_METHOD_NOT_ALLOWED);
        }
    }

    @SuppressWarnings("BusyWait")
    private void handleEventStreaming(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, SessionController sessionController)
    {
        SessionId sessionId = requireSessionId(request);

        Stopwatch timeoutStopwatch = Stopwatch.createStarted();
        Stopwatch pingStopwatch = Stopwatch.createStarted();

        InternalMessageWriter messageWriter = new InternalMessageWriter(response);
        InternalRequestContext requestContext = new InternalRequestContext(objectMapper, Optional.of(sessionController), request, messageWriter, Optional.empty(), taskController);

        Optional.ofNullable(request.getHeader(HEADER_LAST_EVENT_ID))
                .ifPresent(lastEventId -> replaySentMessages(sessionController, sessionId, lastEventId, messageWriter));

        while (timeoutStopwatch.elapsed().compareTo(streamingTimeout) < 0) {
            if (!sessionController.validateSession(sessionId)) {
                log.warn(String.format("Session validation failed for %s", sessionId));
                break;
            }

            BiConsumer<String, Optional<Object>> notifier = (method, params) -> {
                requestContext.sendMessage(method, params);

                pingStopwatch.reset().start();
            };

            mcpServer.reconcileVersions(request, authenticated, messageWriter);

            checkSaveSentMessages(sessionController, sessionId, messageWriter);

            if (pingStopwatch.elapsed().compareTo(streamingPingThreshold) >= 0) {
                notifier.accept(METHOD_PING, Optional.empty());
            }

            try {
                Thread.sleep(streamingPingThreshold.toMillis());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Event streaming interrupted for session %s", sessionId);
                break;
            }
        }
    }

    private void replaySentMessages(SessionController sessionController, SessionId sessionId, String lastEventId, InternalMessageWriter messageWriter)
    {
        sessionController.getSessionValue(sessionId, SENT_MESSAGES)
                .ifPresent(sentMessages -> {
                    boolean found = false;
                    for (SentMessage sentMessage : sentMessages.messages()) {
                        if (found) {
                            log.info("Sending resumable messages to session %s", sessionId);
                            messageWriter.internalWriteMessage(sentMessage.id(), sentMessage.data());
                        }
                        else {
                            found = sentMessage.id().equals(lastEventId);
                        }
                    }
                    messageWriter.flushMessages();
                });
    }

    private void handleMcpDeleteRequest(HttpServletRequest request, HttpServletResponse response)
    {
        sessionController.ifPresentOrElse(controller -> {
            controller.deleteSession(requireSessionId(request));
            response.setStatus(SC_ACCEPTED);
        }, () -> response.setStatus(SC_METHOD_NOT_ALLOWED));
    }

    private void handleMcpPostRequest(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated)
            throws Exception
    {
        InternalMessageWriter messageWriter = new InternalMessageWriter(response);
        request.setAttribute(MESSAGE_WRITER_ATTRIBUTE, messageWriter);

        String accept = request.getHeader(ACCEPT);
        if (accept == null || !(accept.contains(APPLICATION_JSON) && accept.contains(SERVER_SENT_EVENTS))) {
            throw exception(INVALID_REQUEST, "Both application/json and text/event-stream required in Accept header");
        }

        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding(UTF_8.name());

        try (BufferedReader reader = request.getReader()) {
            String body = reader.lines().collect(joining("\n"));

            Object message = deserializeJsonRpcMessage(body);
            switch (message) {
                case JsonRpcRequest<?> rpcRequest when (rpcRequest.id() != null) -> handleRpcRequest(request, response, authenticated, rpcRequest, messageWriter);
                case JsonRpcRequest<?> rpcRequest -> handleRpcNotification(request, response, rpcRequest);
                case JsonRpcResponse<?> rpcResponse -> handleRpcResponse(request, response, authenticated, rpcResponse);
                default -> throw exception(SC_BAD_REQUEST, "The server accepts either requests or notifications");
            }
        }

        sessionController.ifPresent(controller -> optionalSessionId(request)
                .ifPresent(sessionId -> checkSaveSentMessages(controller, sessionId, messageWriter)));
    }

    private void checkSaveSentMessages(SessionController sessionController, SessionId sessionId, InternalMessageWriter messageWriter)
    {
        List<SentMessage> sentMessages = messageWriter.takeSentMessages();
        if (sentMessages.isEmpty()) {
            return;
        }

        sessionController.computeSessionValue(sessionId, SENT_MESSAGES, current -> {
            SentMessages currentSentMessages = current.orElseGet(SentMessages::new);
            return Optional.of(currentSentMessages.withAdditionalMessages(sentMessages, maxResumableMessages));
        });
    }

    @SuppressWarnings("rawtypes")
    private void handleRpcResponse(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, JsonRpcResponse<?> rpcResponse)
    {
        log.debug("Processing MCP response: %s, session: %s", rpcResponse.id(), optionalSessionId(request).map(SessionId::id).orElse("-"));

        Optional<String> maybeTaskId = rpcResponse.result().flatMap(result -> switch (result) {
            case Meta meta -> meta.meta().flatMap(values -> Optional.ofNullable(values.get(META_RELATED_TASK))).map(String::valueOf);
            case Map<?, ?> mapResult -> Optional.ofNullable(mapResult.get(META_FIELD_NAME))
                    .map(meta -> {
                        if (meta instanceof Map<?, ?> metaMap) {
                            return metaMap;
                        }
                        throw exception("Invalid meta field in RPC response");
                    })
                    .map(meta -> meta.get(META_RELATED_TASK)).map(String::valueOf);
            default -> Optional.empty();
        });

        maybeTaskId.ifPresentOrElse(taskId -> {
            InternalMessageWriter messageWriter = new InternalMessageWriter(response);
            setTaskContextIdAttribute(request, authenticated, "");
            request.setAttribute(RPC_REQUEST_ID_ATTRIBUTE, rpcResponse.id());
            mcpServer.acceptTaskResponse(request, messageWriter, taskId, rpcResponse);
        }, () -> sessionController.ifPresent(controller -> {
            SessionId sessionId = requireSessionId(request);
            SessionValueKey<JsonRpcResponse> responseKey = serverToClientResponseKey(rpcResponse.id());
            controller.setSessionValue(sessionId, responseKey, rpcResponse);
        }));

        response.setStatus(SC_ACCEPTED);
    }

    private void handleRpcNotification(HttpServletRequest request, HttpServletResponse response, JsonRpcRequest<?> rpcRequest)
    {
        log.debug("Processing MCP notification: %s, session: %s", rpcRequest.method(), request.getHeader(HEADER_SESSION_ID));

        switch (rpcRequest.method()) {
            case NOTIFICATION_INITIALIZED -> {} // ignore
            case NOTIFICATION_CANCELLED -> handleRpcCancellation(request, convertParams(rpcRequest, CancelledNotification.class));
            case NOTIFICATION_ROOTS_LIST_CHANGED -> handleRpcRootsChanged(request);
            default -> log.warn("Unknown MCP notification method: %s", rpcRequest.method());
        }

        response.setStatus(SC_ACCEPTED);
    }

    private void handleRpcRootsChanged(HttpServletRequest request)
    {
        sessionController.ifPresent(controller -> {
            SessionId sessionId = requireSessionId(request);
            controller.deleteSessionValue(sessionId, ROOTS);

            log.info("Handling roots/list_changed notification for session %s", sessionId);
        });
    }

    private void handleRpcRequest(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest, InternalMessageWriter messageWriter)
            throws Exception
    {
        String rpcMethod = rpcRequest.method();
        Object requestId = rpcRequest.id();


        updateRequestSpan(request, span -> span.setAttribute(MCP_METHOD_NAME, rpcMethod));

        request.setAttribute(RPC_MESSAGE_ATTRIBUTE, rpcRequest);
        request.setAttribute(RPC_REQUEST_ID_ATTRIBUTE, requestId);

        log.debug("Processing MCP request: %s, session: %s", rpcMethod, request.getHeader(HEADER_SESSION_ID));

        setTaskContextIdAttribute(request, authenticated, rpcRequest.method());

        Protocol currentProtocol = protocol(sessionController, request);

        Object result = switch (rpcMethod) {
            case METHOD_INITIALIZE -> mcpServer.initialize(request, response, authenticated, convertParams(rpcRequest, InitializeRequest.class));
            case METHOD_TOOLS_LIST -> withManagement(request, authenticated, requestId, messageWriter, () -> mcpServer.listTools(currentProtocol, authenticated, convertParams(rpcRequest, ListRequest.class)));
            case METHOD_TOOLS_CALL -> withManagement(request, authenticated, requestId, messageWriter, () -> mcpServer.callTool(request, authenticated, messageWriter, convertParams(rpcRequest, CallToolRequest.class)));
            case METHOD_PROMPT_LIST -> withManagement(request, authenticated, requestId, messageWriter, () -> mcpServer.listPrompts(currentProtocol, authenticated, convertParams(rpcRequest, ListRequest.class)));
            case METHOD_PROMPT_GET -> withManagement(request, authenticated, requestId, messageWriter, () -> mcpServer.getPrompt(request, authenticated, messageWriter, convertParams(rpcRequest, GetPromptRequest.class)));
            case METHOD_RESOURCES_LIST -> withManagement(request, authenticated, requestId, messageWriter, () -> mcpServer.listResources(currentProtocol, authenticated, convertParams(rpcRequest, ListRequest.class)));
            case METHOD_RESOURCES_TEMPLATES_LIST -> withManagement(request, authenticated, requestId, messageWriter, () -> mcpServer.listResourceTemplates(currentProtocol, authenticated, convertParams(rpcRequest, ListRequest.class)));
            case METHOD_RESOURCES_READ -> withManagement(request, authenticated, requestId, messageWriter, () -> mcpServer.readResources(request, authenticated, messageWriter, convertParams(rpcRequest, ReadResourceRequest.class)));
            case METHOD_PING -> ImmutableMap.of();
            case METHOD_COMPLETION_COMPLETE -> mcpServer.completionComplete(request, authenticated, messageWriter, convertParams(rpcRequest, CompleteRequest.class));
            case METHOD_LOGGING_SET_LEVEL -> mcpServer.setLoggingLevel(request, convertParams(rpcRequest, SetLevelRequest.class));
            case METHOD_RESOURCES_SUBSCRIBE -> mcpServer.resourcesSubscribe(request, authenticated, messageWriter, convertParams(rpcRequest, SubscribeRequest.class));
            case METHOD_RESOURCES_UNSUBSCRIBE -> mcpServer.resourcesUnsubscribe(request, convertParams(rpcRequest, SubscribeRequest.class));
            case METHOD_LIST_TASKS -> mcpServer.listTasks(request, messageWriter, convertParams(rpcRequest, ListRequest.class));
            case METHOD_GET_TASK -> mcpServer.getTask(request, messageWriter, convertParams(rpcRequest, GetTaskRequest.class));
            case METHOD_GET_TASK_RESULT -> mcpServer.blockUntilTaskCompletion(request, messageWriter, convertParams(rpcRequest, GetTaskRequest.class));
            case METHOD_CANCEL_TASK -> mcpServer.blockUntilTaskCancelled(request, messageWriter, convertParams(rpcRequest, GetTaskRequest.class));
            default -> throw exception(METHOD_NOT_FOUND, "Unknown method: " + rpcRequest.method());
        };

        response.setStatus(SC_OK);

        JsonRpcMessage rpcMessage = switch (result) {
            case JsonRpcRequest<?> requestResult -> requestResult;
            case JsonRpcResponse<?> responseResult -> new JsonRpcResponse<>(rpcRequest.id(), responseResult.error(), responseResult.result());
            default -> new JsonRpcResponse<>(rpcRequest.id(), Optional.empty(), Optional.of(result));
        };

        messageWriter.write(objectMapper.writeValueAsString(rpcMessage));
        messageWriter.flushMessages();
    }

    private void setTaskContextIdAttribute(HttpServletRequest request, Authenticated<?> authenticated, String rpcMethod)
    {
        Optional<SessionId> maybeSessionId = validateSession(request, rpcMethod);
        Optional<TaskContextId> maybeTaskContextId = maybeSessionId.flatMap(sessionId -> optionalTaskContextId(authenticated, sessionId));
        maybeTaskContextId.ifPresent(taskContextId -> request.setAttribute(TASK_CONTEXT_ID_ATTRIBUTE, taskContextId));
    }

    private Optional<TaskContextId> optionalTaskContextId(McpIdentity identity, SessionId sessionId)
    {
        return taskContextMapper.map(mapper -> mapper.map(identity, sessionId));
    }

    private void handleRpcCancellation(HttpServletRequest request, CancelledNotification cancelledNotification)
    {
        sessionController.ifPresent(controller -> {
            SessionId sessionId = requireSessionId(request);
            SessionValueKey<CancelledNotification> cancellationKey = cancellationKey(cancelledNotification.requestId());
            controller.setSessionValue(sessionId, cancellationKey, cancelledNotification);
        });
    }

    private Object withManagement(HttpServletRequest request, Authenticated<?> authenticated, Object requestId, InternalMessageWriter messageWriter, Supplier<Object> supplier)
    {
        if (sessionController.isEmpty()) {
            return supplier.get();
        }

        if (!httpGetEventsEnabled) {
            mcpServer.reconcileVersions(request, authenticated, messageWriter);
        }

        return cancellationController.builder(requireSessionId(request), cancellationKey(requestId))
                .withIsCancelledCondition(_ -> true)
                .withReasonMapper(cancellation -> cancellation.flatMap(CancelledNotification::reason))
                .withRequestId(requestId)
                .withPostCancellationAction((sessionId, key) -> sessionController.ifPresent(controller -> controller.deleteSessionValue(sessionId, key)))
                .executeCancellable(supplier);
    }

    private Optional<SessionId> validateSession(HttpServletRequest request, String rpcMethod)
    {
        if (!METHOD_INITIALIZE.equals(rpcMethod)) {
            return sessionController.map(controller -> {
                SessionId sessionId = requireSessionId(request);
                if (!controller.validateSession(sessionId)) {
                    throw new WebApplicationException(NOT_FOUND);
                }
                return sessionId;
            });
        }

        return Optional.empty();
    }

    private <T> T convertParams(JsonRpcRequest<?> rpcRequest, Class<T> clazz)
    {
        Object value = rpcRequest.params().map(v -> (Object) v).orElseGet(ImmutableMap::of);
        return objectMapper.convertValue(value, clazz);
    }

    private boolean isMcpRequest(HttpServletRequest request)
    {
        if (ALLOWED_HTTP_METHODS.contains(request.getMethod().toUpperCase(ROOT))) {
            return metadata.uriPath().equals(request.getRequestURI());
        }
        return false;
    }

    private Object deserializeJsonRpcMessage(String json)
            throws Exception
    {
        JsonNode tree = objectMapper.readTree(json);

        if (tree.has("method")) {
            return objectMapper.convertValue(tree, JsonRpcRequest.class);
        }

        if (tree.has("result") || tree.has("error")) {
            return objectMapper.convertValue(tree, JsonRpcResponse.class);
        }

        throw exception(PARSE_ERROR, "Cannot deserialize JsonRpcMessage: " + json);
    }
}
