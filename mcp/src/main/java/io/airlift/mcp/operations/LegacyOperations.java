package io.airlift.mcp.operations;

import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.airlift.log.Logger;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.SentMessages;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.Content;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.InitializeResult.CompletionCapabilities;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.OptionalBoolean;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.SetLevelRequest;
import io.airlift.mcp.model.SubscribeListChanged;
import io.airlift.mcp.model.SubscribeRequest;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.reflection.IconHelper;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiConsumer;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.http.server.tracing.TracingServletFilter.updateRequestSpan;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpModule.MCP_SERVER_ICONS;
import static io.airlift.mcp.model.Constants.HEADER_LAST_EVENT_ID;
import static io.airlift.mcp.model.Constants.HEADER_SESSION_ID;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.Constants.MESSAGE_WRITER_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
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
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.METHOD_NOT_FOUND;
import static io.airlift.mcp.model.JsonRpcErrorCode.RESOURCE_NOT_FOUND;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.sessions.SessionValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.sessions.SessionValueKey.PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.ROOTS;
import static io.airlift.mcp.sessions.SessionValueKey.SENT_MESSAGES;
import static io.airlift.mcp.sessions.SessionValueKey.cancellationKey;
import static io.airlift.mcp.sessions.SessionValueKey.serverToClientResponseKey;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_METHOD_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_RESOURCE_URI;
import static jakarta.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static java.util.Objects.requireNonNull;

class LegacyOperations
        implements Operations
{
    private static final Logger log = Logger.get(LegacyOperations.class);

    private final McpMetadata metadata;
    private final JsonMapper jsonMapper;
    private final Optional<SessionController> sessionController;
    private final LegacyCancellationController cancellationController;
    private final boolean httpGetEventsEnabled;
    private final Duration streamingPingThreshold;
    private final Duration streamingTimeout;
    private final int maxResumableMessages;
    private final LegacyVersionsController versionsController;
    private final Duration sessionTimeout;
    private final McpEntities entities;
    private final Implementation serverImplementation;
    private final PaginationUtil paginationUtil;

    @Inject
    LegacyOperations(
            McpMetadata metadata,
            JsonMapper jsonMapper,
            Optional<SessionController> sessionController,
            LegacyCancellationController cancellationController,
            McpConfig mcpConfig,
            LegacyVersionsController versionsController,
            IconHelper iconHelper,
            @Named(MCP_SERVER_ICONS) Set<String> serverIcons,
            McpEntities entities)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.cancellationController = requireNonNull(cancellationController, "cancellationController is null");
        this.versionsController = requireNonNull(versionsController, "versionsController is null");
        this.entities = requireNonNull(entities, "entities is null");

        httpGetEventsEnabled = mcpConfig.isHttpGetEventsEnabled();
        streamingPingThreshold = mcpConfig.getEventStreamingPingThreshold().toJavaTime();
        streamingTimeout = mcpConfig.getEventStreamingTimeout().toJavaTime();
        maxResumableMessages = mcpConfig.getMaxResumableMessages();
        sessionTimeout = mcpConfig.getDefaultSessionTimeout().toJavaTime();

        serverImplementation = iconHelper.mapIcons(serverIcons).map(icons -> metadata.implementation().withAdditionalIcons(icons))
                .orElse(metadata.implementation());

        paginationUtil = new PaginationUtil(mcpConfig);
    }

    @Override
    public void handleRpcRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest)
    {
        String method = rpcRequest.method();
        Object requestId = rpcRequest.id();

        updateRequestSpan(request, span -> span.setAttribute(MCP_METHOD_NAME, method));
        setSessionSpan(request);

        log.debug("Processing MCP request: %s, session: %s", method, request.getHeader(HEADER_SESSION_ID));

        validateSession(request, rpcRequest);

        MessageWriterImpl messageWriter = new MessageWriterImpl(response);
        request.setAttribute(MESSAGE_WRITER_ATTRIBUTE, messageWriter);

        RequestContextImpl requestContext = new RequestContextImpl(jsonMapper, sessionController, request, response, messageWriter, authenticated);

        Object result = switch (method) {
            case METHOD_INITIALIZE -> handleInitialize(requestContext, convertParams(rpcRequest, InitializeRequest.class));
            case METHOD_TOOLS_LIST -> withManagement(requestContext, requestId, () -> handleListTools(requestContext, convertParams(rpcRequest, ListRequest.class)));
            case METHOD_TOOLS_CALL -> withManagement(requestContext, requestId, () -> handleCallTool(requestContext, convertParams(rpcRequest, CallToolRequest.class)));
            case METHOD_PROMPT_LIST -> withManagement(requestContext, requestId, () -> handleListPrompts(requestContext, convertParams(rpcRequest, ListRequest.class)));
            case METHOD_PROMPT_GET -> withManagement(requestContext, requestId, () -> handleGetPrompt(requestContext, convertParams(rpcRequest, GetPromptRequest.class)));
            case METHOD_RESOURCES_LIST -> withManagement(requestContext, requestId, () -> handleListResources(requestContext, convertParams(rpcRequest, ListRequest.class)));
            case METHOD_RESOURCES_TEMPLATES_LIST -> withManagement(requestContext, requestId, () -> handleListResourceTemplates(requestContext, convertParams(rpcRequest, ListRequest.class)));
            case METHOD_RESOURCES_READ -> withManagement(requestContext, requestId, () -> handleReadResources(requestContext, convertParams(rpcRequest, ReadResourceRequest.class)));
            case METHOD_COMPLETION_COMPLETE -> handleCompletionComplete(requestContext, convertParams(rpcRequest, CompleteRequest.class));
            case METHOD_LOGGING_SET_LEVEL -> handleSetLoggingLevel(requestContext, convertParams(rpcRequest, SetLevelRequest.class));
            case METHOD_RESOURCES_SUBSCRIBE -> handleResourcesSubscribe(requestContext, convertParams(rpcRequest, SubscribeRequest.class));
            case METHOD_RESOURCES_UNSUBSCRIBE -> handleResourcesUnsubscribe(requestContext, convertParams(rpcRequest, SubscribeRequest.class));
            case METHOD_PING -> ImmutableMap.of();
            default -> throw exception(METHOD_NOT_FOUND, "Unknown method: " + method);
        };

        response.setStatus(SC_OK);

        JsonRpcResponse<?> rpcResponse = new JsonRpcResponse<>(requestId, Optional.empty(), Optional.of(result));
        messageWriter.write(jsonMapper.writeValueAsString(rpcResponse));
        messageWriter.flushMessages();

        sessionController.ifPresent(controller -> optionalSessionId(request)
                .ifPresent(sessionId -> checkSaveSentMessages(controller, sessionId, messageWriter)));
    }

    @Override
    public void handleRpcNotification(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest)
    {
        setSessionSpan(request);

        switch (rpcRequest.method()) {
            case NOTIFICATION_INITIALIZED -> {} // ignore
            case NOTIFICATION_CANCELLED -> handleRpcCancellation(request, convertParams(rpcRequest, CancelledNotification.class));
            case NOTIFICATION_ROOTS_LIST_CHANGED -> handleRpcRootsChanged(request);
            default -> log.warn("Unknown MCP notification method: %s", rpcRequest.method());
        }

        response.setStatus(SC_ACCEPTED);
    }

    @Override
    public void handleRpcResponse(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcResponse<?> rpcResponse)
    {
        setSessionSpan(request);

        sessionController.ifPresent(controller ->
                controller.setSessionValue(requireSessionId(request), serverToClientResponseKey(rpcResponse.id()), rpcResponse));

        response.setStatus(SC_ACCEPTED);
    }

    @Override
    public void handleRcpDeleteRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated)
    {
        setSessionSpan(request);

        sessionController.ifPresentOrElse(controller -> {
            controller.deleteSession(requireSessionId(request));
            response.setStatus(SC_ACCEPTED);
        }, () -> response.setStatus(SC_METHOD_NOT_ALLOWED));
    }

    @Override
    public void handleRpcGetRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated)
    {
        setSessionSpan(request);

        boolean wasHandled = httpGetEventsEnabled && sessionController.map(controller -> {
            handleEventStreaming(request, response, authenticated, controller);
            return true;
        }).orElse(false);

        if (!wasHandled) {
            response.setStatus(SC_METHOD_NOT_ALLOWED);
        }
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

    private InitializeResult handleInitialize(RequestContextImpl requestContext, InitializeRequest initializeRequest)
    {
        Protocol protocol = Protocol.of(initializeRequest.protocolVersion())
                .orElse(LATEST_PROTOCOL);

        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_PROTOCOL_VERSION, protocol.value()));

        boolean sessionsEnabled = sessionController.map(controller -> {
            SessionId sessionId = controller.createSession(requestContext.identity(), Optional.of(sessionTimeout));
            RequestContextImpl localRequestContext = requestContext.withSessionId(sessionId);

            localRequestContext.response().addHeader(MCP_SESSION_ID, sessionId.id());

            versionsController.initializeSessionVersions(localRequestContext);

            controller.setSessionValue(sessionId, LOGGING_LEVEL, LoggingLevel.INFO);
            controller.setSessionValue(sessionId, CLIENT_CAPABILITIES, initializeRequest.capabilities());
            controller.setSessionValue(sessionId, PROTOCOL, protocol);

            updateRequestSpan(localRequestContext.request(), span -> span.setAttribute(MCP_SESSION_ID, sessionId.id()));

            return true;
        }).orElse(false);

        List<CompleteReference> completions = entities.completions(requestContext);
        List<Prompt> prompts = entities.prompts(requestContext);

        List<Resource> resources = entities.resources(requestContext);
        List<ResourceTemplate> resourceTemplates = entities.resourceTemplates(requestContext);
        List<Tool> tools = entities.tools(requestContext);

        InitializeResult.ServerCapabilities serverCapabilities = new InitializeResult.ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new CompletionCapabilities()),
                sessionsEnabled ? Optional.of(new InitializeResult.LoggingCapabilities()) : Optional.empty(),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)),
                resources.isEmpty() && resourceTemplates.isEmpty() ? Optional.empty() : Optional.of(new SubscribeListChanged(sessionsEnabled, sessionsEnabled)),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)),
                Optional.empty());

        Implementation localImplementation = protocol.supportsIcons() ? serverImplementation : serverImplementation.simpleForm();

        return new InitializeResult(protocol.value(), serverCapabilities, localImplementation, metadata.instructions());
    }

    private ListToolsResult handleListTools(RequestContextImpl requestContext, ListRequest listRequest)
    {
        List<Tool> localTools = entities.tools(requestContext)
                .stream()
                .map(tool -> requestContext.protocol().supportsIcons() ? tool : tool.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localTools, Tool::name, ListToolsResult::new);
    }

    private CallToolResult handleCallTool(RequestContextImpl requestContext, CallToolRequest callToolRequest)
    {
        entities.validateToolAllowed(requestContext, callToolRequest.name());

        ToolEntry toolEntry = entities.toolEntry(requestContext, callToolRequest.name())
                .orElseThrow(() -> exception(INVALID_PARAMS, "Tool not found: " + callToolRequest.name()));

        try {
            return toolEntry.toolHandler().callTool(requestContext.withProgressToken(progressToken(callToolRequest)), callToolRequest);
        }
        catch (McpClientException mcpClientException) {
            return new CallToolResult(ImmutableList.of(new Content.TextContent(mcpClientException.unwrap().errorDetail().message())), Optional.empty(), true, Optional.empty());
        }
    }

    private ListPromptsResult handleListPrompts(RequestContextImpl requestContext, ListRequest listRequest)
    {
        List<Prompt> localPrompts = entities.prompts(requestContext)
                .stream()
                .map(prompt -> requestContext.protocol().supportsIcons() ? prompt : prompt.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localPrompts, Prompt::name, ListPromptsResult::new);
    }

    private GetPromptResult handleGetPrompt(RequestContextImpl requestContext, GetPromptRequest getPromptRequest)
    {
        entities.validatePromptAllowed(requestContext, getPromptRequest.name());

        PromptEntry promptEntry = entities.promptEntry(requestContext, getPromptRequest.name())
                .orElseThrow(() -> exception(INVALID_PARAMS, "Prompt not found: " + getPromptRequest.name()));

        return promptEntry.promptHandler().getPrompt(requestContext.withProgressToken(progressToken(getPromptRequest)), getPromptRequest);
    }

    private ListResourcesResult handleListResources(RequestContextImpl requestContext, ListRequest listRequest)
    {
        List<Resource> localResources = entities.resources(requestContext)
                .stream()
                .map(resource -> requestContext.protocol().supportsIcons() ? resource : resource.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResources, Resource::name, ListResourcesResult::new);
    }

    private ListResourceTemplatesResult handleListResourceTemplates(RequestContextImpl requestContext, ListRequest listRequest)
    {
        List<ResourceTemplate> localResourceTemplates = entities.resourceTemplates(requestContext)
                .stream()
                .map(resourceTemplate -> requestContext.protocol().supportsIcons() ? resourceTemplate : resourceTemplate.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResourceTemplates, ResourceTemplate::name, ListResourceTemplatesResult::new);
    }

    private Object handleReadResources(RequestContextImpl requestContext, ReadResourceRequest readResourceRequest)
    {
        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, readResourceRequest.uri()));

        List<ResourceContents> resourceContents = entities.readResourceContents(requestContext.withProgressToken(progressToken(readResourceRequest)), readResourceRequest)
                .orElseThrow(() -> exception(RESOURCE_NOT_FOUND, "Resource not found: " + readResourceRequest.uri()));

        return new ReadResourceResult(resourceContents);
    }

    private CompleteResult handleCompletionComplete(RequestContextImpl requestContext, CompleteRequest completeRequest)
    {
        return entities.completionEntry(requestContext, completeRequest.ref())
                .map(completionEntry -> completionEntry.handler().complete(requestContext.withProgressToken(progressToken(completeRequest)), completeRequest))
                .orElseGet(() -> new CompleteResult(new CompleteResult.CompleteCompletion(ImmutableList.of(), OptionalInt.empty(), OptionalBoolean.UNDEFINED)));
    }

    private Object handleSetLoggingLevel(RequestContextImpl requestContext, SetLevelRequest setLevelRequest)
    {
        requestContext.session().setValue(LOGGING_LEVEL, setLevelRequest.level());
        return ImmutableMap.of();
    }

    private Object handleResourcesSubscribe(RequestContextImpl requestContext, SubscribeRequest subscribeRequest)
    {
        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, subscribeRequest.uri()));

        if (entities.resourceEntry(requestContext, subscribeRequest.uri()).isEmpty()) {
            throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + subscribeRequest.uri()));
        }

        versionsController.resourcesSubscribe(requestContext.withProgressToken(progressToken(subscribeRequest)), subscribeRequest);

        return ImmutableMap.of();
    }

    private Object handleResourcesUnsubscribe(RequestContextImpl requestContext, SubscribeRequest subscribeRequest)
    {
        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, subscribeRequest.uri()));

        if (entities.resourceEntry(requestContext, subscribeRequest.uri()).isEmpty()) {
            throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + subscribeRequest.uri()));
        }

        versionsController.resourcesUnsubscribe(requestContext, subscribeRequest.uri());

        return ImmutableMap.of();
    }

    private void handleRpcCancellation(HttpServletRequest request, CancelledNotification cancelledNotification)
    {
        sessionController.ifPresent(controller -> {
            SessionId sessionId = requireSessionId(request);
            SessionValueKey<CancelledNotification> cancellationKey = cancellationKey(cancelledNotification.requestId());
            controller.setSessionValue(sessionId, cancellationKey, cancelledNotification);
        });
    }

    private void handleRpcRootsChanged(HttpServletRequest request)
    {
        sessionController.ifPresent(controller -> {
            SessionId sessionId = requireSessionId(request);
            controller.deleteSessionValue(sessionId, ROOTS);

            log.info("Handling roots/list_changed notification for session %s", sessionId);
        });
    }

    private <T> T convertParams(JsonRpcRequest<?> rpcRequest, Class<T> clazz)
    {
        Object value = rpcRequest.params().map(v -> (Object) v).orElseGet(ImmutableMap::of);
        return jsonMapper.convertValue(value, clazz);
    }

    private Object withManagement(RequestContextImpl requestContext, Object requestId, Supplier<Object> supplier)
    {
        if (sessionController.isEmpty()) {
            return supplier.get();
        }

        if (!httpGetEventsEnabled) {
            versionsController.reconcileVersions(requestContext);
        }

        return cancellationController.builder(requestContext.session().sessionId(), cancellationKey(requestId))
                .withIsCancelledCondition(Optional::isPresent)
                .withReasonMapper(cancellation -> cancellation.flatMap(CancelledNotification::reason))
                .withRequestId(requestId)
                .withPostCancellationAction((sessionId, key) -> sessionController.ifPresent(controller -> controller.deleteSessionValue(sessionId, key)))
                .executeCancellable(supplier);
    }

    @SuppressWarnings("BusyWait")
    private void handleEventStreaming(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, SessionController sessionController)
    {
        SessionId sessionId = requireSessionId(request);

        Stopwatch timeoutStopwatch = Stopwatch.createStarted();
        Stopwatch pingStopwatch = Stopwatch.createStarted();

        MessageWriterImpl messageWriter = new MessageWriterImpl(response);
        RequestContextImpl requestContext = new RequestContextImpl(jsonMapper, Optional.of(sessionController), request, response, messageWriter, authenticated);

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

            versionsController.reconcileVersions(requestContext);

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

    private void replaySentMessages(SessionController sessionController, SessionId sessionId, String lastEventId, MessageWriterImpl messageWriter)
    {
        sessionController.getSessionValue(sessionId, SENT_MESSAGES)
                .ifPresent(sentMessages -> {
                    boolean found = false;
                    for (SentMessages.SentMessage sentMessage : sentMessages.messages()) {
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

    private void checkSaveSentMessages(SessionController sessionController, SessionId sessionId, MessageWriterImpl messageWriter)
    {
        List<SentMessages.SentMessage> sentMessages = messageWriter.takeSentMessages();
        if (sentMessages.isEmpty()) {
            return;
        }

        sessionController.computeSessionValue(sessionId, SENT_MESSAGES, current -> {
            SentMessages currentSentMessages = current.orElseGet(SentMessages::new);
            return Optional.of(currentSentMessages.withAdditionalMessages(sentMessages, maxResumableMessages));
        });
    }

    private void validateSession(HttpServletRequest request, JsonRpcRequest<?> rpcRequest)
    {
        if (!METHOD_INITIALIZE.equals(rpcRequest.method())) {
            sessionController.ifPresent(controller -> {
                if (!controller.validateSession(requireSessionId(request))) {
                    throw new WebApplicationException(NOT_FOUND);
                }
            });
        }
    }

    private Optional<Object> progressToken(Meta meta)
    {
        return meta.meta().flatMap(m -> Optional.ofNullable(m.get("progressToken")));
    }

    private void setSessionSpan(HttpServletRequest request)
    {
        optionalSessionId(request)
                .ifPresent(sessionId ->
                        updateRequestSpan(request, span -> span.setAttribute(MCP_SESSION_ID, sessionId.id())));
    }
}
