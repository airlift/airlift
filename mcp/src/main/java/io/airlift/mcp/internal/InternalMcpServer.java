package io.airlift.mcp.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.log.Logger;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.CompletionHandler;
import io.airlift.mcp.handler.MessageWriter;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.PromptHandler;
import io.airlift.mcp.handler.ResourceEntry;
import io.airlift.mcp.handler.ResourceHandler;
import io.airlift.mcp.handler.ResourceTemplateEntry;
import io.airlift.mcp.handler.ResourceTemplateHandler;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.CompleteReference.PromptReference;
import io.airlift.mcp.model.CompleteReference.ResourceReference;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.CompleteResult.CompleteCompletion;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.GetTaskRequest;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.InitializeResult.CompletionCapabilities;
import io.airlift.mcp.model.InitializeResult.LoggingCapabilities;
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;
import io.airlift.mcp.model.InitializeResult.TaskCapabilities;
import io.airlift.mcp.model.InitializeResult.TaskRequests;
import io.airlift.mcp.model.InitializeResult.TaskTools;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListTasksResult;
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
import io.airlift.mcp.model.ResourceTemplateValues;
import io.airlift.mcp.model.ResourcesUpdatedNotification;
import io.airlift.mcp.model.SetLevelRequest;
import io.airlift.mcp.model.SubscribeListChanged;
import io.airlift.mcp.model.SubscribeRequest;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.reflection.IconHelper;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import io.airlift.mcp.tasks.TaskController;
import io.airlift.mcp.tasks.TaskFacade;
import io.airlift.mcp.versions.ResourceVersion;
import io.airlift.mcp.versions.SystemListVersions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.glassfish.jersey.uri.UriTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.http.server.tracing.TracingServletFilter.updateRequestSpan;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpModule.MCP_SERVER_ICONS;
import static io.airlift.mcp.internal.InternalFilter.MCP_PROTOCOL_VERSION;
import static io.airlift.mcp.internal.InternalFilter.MCP_RESOURCE_URI;
import static io.airlift.mcp.internal.InternalRequestContext.requireSessionId;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.PROGRESS_TOKEN;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.REQUEST_TIMEOUT;
import static io.airlift.mcp.model.JsonRpcErrorCode.RESOURCE_NOT_FOUND;
import static io.airlift.mcp.model.Property.INSTANCE;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.sessions.SessionValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.sessions.SessionValueKey.PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.SYSTEM_LIST_VERSIONS;
import static io.airlift.mcp.sessions.SessionValueKey.resourceVersionKey;
import static io.airlift.mcp.tasks.TaskConditions.hasMessage;
import static io.airlift.mcp.tasks.TaskConditions.isCompleted;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class InternalMcpServer
        implements McpServer
{
    private static final Logger log = Logger.get(InternalMcpServer.class);

    private static final int RECONCILE_PAGE_SIZE = 100;

    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();
    private final Map<String, PromptEntry> prompts = new ConcurrentHashMap<>();
    private final Map<URI, ResourceEntry> resources = new ConcurrentHashMap<>();
    private final Map<UriTemplate, ResourceTemplateEntry> resourceTemplates = new ConcurrentHashMap<>();
    private final Map<String, CompletionEntry> completions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final McpMetadata metadata;
    private final LifeCycleManager lifeCycleManager;
    private final PaginationUtil paginationUtil;
    private final Optional<SessionController> sessionController;
    private final Duration sessionTimeout;
    private final Optional<TaskController> taskController;
    private final Implementation serverImplementation;
    private final Duration pingThreshold;
    private final Duration timeout;

    @Inject
    InternalMcpServer(
            ObjectMapper objectMapper,
            McpMetadata metadata,
            LifeCycleManager lifeCycleManager,
            Optional<SessionController> sessionController,
            Set<ToolEntry> tools,
            Set<PromptEntry> prompts,
            Set<ResourceEntry> resources,
            Set<ResourceTemplateEntry> resourceTemplates,
            Set<CompletionEntry> completions,
            PaginationUtil paginationUtil,
            McpConfig mcpConfig,
            IconHelper iconHelper,
            @Named(MCP_SERVER_ICONS) Set<String> serverIcons,
            Optional<TaskController> taskController)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.lifeCycleManager = requireNonNull(lifeCycleManager, "lifeCycleManager is null");
        this.paginationUtil = requireNonNull(paginationUtil, "paginationUtil is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.taskController = requireNonNull(taskController, "taskController is null");

        tools.forEach(tool -> addTool(tool.tool(), tool.toolHandler()));
        prompts.forEach(prompt -> addPrompt(prompt.prompt(), prompt.promptHandler()));
        resources.forEach(resource -> addResource(resource.resource(), resource.handler()));
        resourceTemplates.forEach(resourceTemplate -> addResourceTemplate(resourceTemplate.resourceTemplate(), resourceTemplate.handler()));
        completions.forEach(completion -> addCompletion(completion.reference(), completion.handler()));

        sessionTimeout = mcpConfig.getDefaultSessionTimeout().toJavaTime();

        serverImplementation = iconHelper.mapIcons(serverIcons).map(icons -> metadata.implementation().withAdditionalIcons(icons))
                .orElse(metadata.implementation());

        pingThreshold = mcpConfig.getEventStreamingPingThreshold().toJavaTime();
        timeout = mcpConfig.getEventStreamingTimeout().toJavaTime();
    }

    @Override
    public void stop()
    {
        lifeCycleManager.stop();
    }

    @Override
    public void addTool(Tool tool, ToolHandler toolHandler)
    {
        tools.put(tool.name(), new ToolEntry(tool, toolHandler));
    }

    @Override
    public void removeTool(String toolName)
    {
        tools.remove(toolName);
    }

    @Override
    public void addPrompt(Prompt prompt, PromptHandler promptHandler)
    {
        prompts.put(prompt.name(), new PromptEntry(prompt, promptHandler));
    }

    @Override
    public void removePrompt(String promptName)
    {
        prompts.remove(promptName);
    }

    @Override
    public void addResource(Resource resource, ResourceHandler handler)
    {
        resources.put(toUri(resource.uri()), new ResourceEntry(resource, handler));
    }

    @Override
    public void removeResource(String resourceUri)
    {
        resources.remove(toUri(resourceUri));
    }

    @Override
    public void addResourceTemplate(ResourceTemplate resourceTemplate, ResourceTemplateHandler handler)
    {
        resourceTemplates.put(toUriTemplate(resourceTemplate.uriTemplate()), new ResourceTemplateEntry(resourceTemplate, handler));
    }

    @Override
    public void removeResourceTemplate(String uriTemplate)
    {
        resourceTemplates.remove(toUriTemplate(uriTemplate));
    }

    @Override
    public void addCompletion(CompleteReference reference, CompletionHandler handler)
    {
        completions.put(completionKey(reference), new CompletionEntry(reference, handler));
    }

    @Override
    public void removeCompletion(CompleteReference reference)
    {
        completions.remove(completionKey(reference));
    }

    InitializeResult initialize(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, InitializeRequest initializeRequest)
    {
        Protocol protocol = Protocol.of(initializeRequest.protocolVersion())
                .orElse(LATEST_PROTOCOL);

        updateRequestSpan(request, span -> span.setAttribute(MCP_PROTOCOL_VERSION, protocol.value()));

        boolean sessionsEnabled = sessionController.map(controller -> {
            SessionId sessionId = controller.createSession(authenticated, Optional.of(sessionTimeout));
            response.addHeader(MCP_SESSION_ID, sessionId.id());

            controller.setSessionValue(sessionId, LOGGING_LEVEL, LoggingLevel.INFO);
            controller.setSessionValue(sessionId, SYSTEM_LIST_VERSIONS, buildSystemListVersions());
            controller.setSessionValue(sessionId, CLIENT_CAPABILITIES, initializeRequest.capabilities());
            controller.setSessionValue(sessionId, PROTOCOL, protocol);

            updateRequestSpan(request, span -> span.setAttribute(MCP_SESSION_ID, sessionId.id()));

            return true;
        }).orElse(false);

        ServerCapabilities serverCapabilities = new ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new CompletionCapabilities()),
                sessionsEnabled ? Optional.of(new LoggingCapabilities()) : Optional.empty(),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)),
                resources.isEmpty() ? Optional.empty() : Optional.of(new SubscribeListChanged(sessionsEnabled, sessionsEnabled)),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)),
                protocol.supportsTasks() ? Optional.of(new TaskCapabilities(INSTANCE, INSTANCE, Optional.of(new TaskRequests(Optional.of(new TaskTools(INSTANCE)))))) : Optional.empty(),
                Optional.empty());

        Implementation localImplementation = protocol.supportsIcons() ? serverImplementation : serverImplementation.simpleForm();

        return new InitializeResult(protocol.value(), serverCapabilities, localImplementation, metadata.instructions());
    }

    ListToolsResult listTools(Protocol protocol, ListRequest listRequest)
    {
        List<Tool> localTools = tools.values().stream()
                .map(ToolEntry::tool)
                .map(tool -> protocol.supportsIcons() ? tool : tool.withoutIcons())
                .map(tool -> protocol.supportsTasks() ? tool.withAdjustedExecution() : tool.withoutExecution())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localTools, Tool::name, ListToolsResult::new);
    }

    ListPromptsResult listPrompts(Protocol protocol, ListRequest listRequest)
    {
        List<Prompt> localPrompts = prompts.values().stream()
                .map(PromptEntry::prompt)
                .map(prompt -> protocol.supportsIcons() ? prompt : prompt.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localPrompts, Prompt::name, ListPromptsResult::new);
    }

    ListResourcesResult listResources(Protocol protocol, ListRequest listRequest)
    {
        List<Resource> localResources = resources.values().stream()
                .map(ResourceEntry::resource)
                .map(resource -> protocol.supportsIcons() ? resource : resource.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResources, Resource::name, ListResourcesResult::new);
    }

    ListResourceTemplatesResult listResourceTemplates(Protocol protocol, ListRequest listRequest)
    {
        List<ResourceTemplate> localResourceTemplates = resourceTemplates.values().stream()
                .map(ResourceTemplateEntry::resourceTemplate)
                .map(resourceTemplate -> protocol.supportsIcons() ? resourceTemplate : resourceTemplate.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResourceTemplates, ResourceTemplate::name, ListResourceTemplatesResult::new);
    }

    CallToolResult callTool(HttpServletRequest request, MessageWriter messageWriter, CallToolRequest callToolRequest)
    {
        ToolEntry toolEntry = tools.get(callToolRequest.name());
        if (toolEntry == null) {
            throw exception(INVALID_PARAMS, "Tool not found: " + callToolRequest.name());
        }

        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(callToolRequest), taskController);
        try {
            return toolEntry.toolHandler().callTool(requestContext, callToolRequest);
        }
        catch (McpClientException mcpClientException) {
            return new CallToolResult(ImmutableList.of(new TextContent(mcpClientException.unwrap().errorDetail().message())), Optional.empty(), true);
        }
    }

    GetPromptResult getPrompt(HttpServletRequest request, MessageWriter messageWriter, GetPromptRequest getPromptRequest)
    {
        PromptEntry promptEntry = prompts.get(getPromptRequest.name());
        if (promptEntry == null) {
            throw exception(INVALID_PARAMS, "Prompt not found: " + getPromptRequest.name());
        }

        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(getPromptRequest), taskController);
        return promptEntry.promptHandler().getPrompt(requestContext, getPromptRequest);
    }

    ReadResourceResult readResources(HttpServletRequest request, MessageWriter messageWriter, ReadResourceRequest readResourceRequest)
    {
        updateRequestSpan(request, span -> span.setAttribute(MCP_RESOURCE_URI, readResourceRequest.uri()));

        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(readResourceRequest), taskController);

        List<ResourceContents> resourceContents = internalReadResource(readResourceRequest, requestContext)
                .orElseThrow(() -> exception(RESOURCE_NOT_FOUND, "Resource not found: " + readResourceRequest.uri()));

        return new ReadResourceResult(resourceContents);
    }

    Object setLoggingLevel(HttpServletRequest request, SetLevelRequest setLevelRequest)
    {
        SessionController localSessionController = sessionController.orElseThrow(() -> exception(INVALID_REQUEST, "set logging level not supported"));
        SessionId sessionId = requireSessionId(request);

        localSessionController.setSessionValue(sessionId, LOGGING_LEVEL, setLevelRequest.level());

        return ImmutableMap.of();
    }

    CompleteResult completionComplete(HttpServletRequest request, InternalMessageWriter messageWriter, CompleteRequest completeRequest)
    {
        CompletionEntry completionEntry = completions.get(completionKey(completeRequest.ref()));
        if (completionEntry == null) {
            return new CompleteResult(new CompleteCompletion(ImmutableList.of(), OptionalInt.empty(), OptionalBoolean.UNDEFINED));
        }

        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(completeRequest), taskController);

        return completionEntry.handler().complete(requestContext, completeRequest);
    }

    Object resourcesSubscribe(HttpServletRequest request, InternalMessageWriter messageWriter, SubscribeRequest subscribeRequest)
    {
        updateRequestSpan(request, span -> span.setAttribute(MCP_RESOURCE_URI, subscribeRequest.uri()));

        SessionController localSessionController = requireSessionController();
        SessionId sessionId = requireSessionId(request);
        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(subscribeRequest), taskController);

        List<ResourceContents> resourceContents = internalReadResource(new ReadResourceRequest(subscribeRequest.uri(), subscribeRequest.meta()), requestContext)
                .orElseThrow(() -> exception(RESOURCE_NOT_FOUND, "Resource not found: " + subscribeRequest.uri()));

        String hash = listHash(resourceContents.stream());
        SessionValueKey<ResourceVersion> key = resourceVersionKey(subscribeRequest.uri());

        localSessionController.setSessionValue(sessionId, key, new ResourceVersion(hash));

        return ImmutableMap.of();
    }

    Object resourcesUnsubscribe(HttpServletRequest request, SubscribeRequest subscribeRequest)
    {
        updateRequestSpan(request, span -> span.setAttribute(MCP_RESOURCE_URI, subscribeRequest.uri()));

        SessionController localSessionController = requireSessionController();
        SessionId sessionId = requireSessionId(request);

        SessionValueKey<ResourceVersion> key = resourceVersionKey(subscribeRequest.uri());
        localSessionController.deleteSessionValue(sessionId, key);

        return ImmutableMap.of();
    }

    void reconcileVersions(HttpServletRequest request, InternalMessageWriter messageWriter)
    {
        SessionController localSessionController = requireSessionController();
        SessionId sessionId = requireSessionId(request);

        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, Optional.empty(), taskController);
        SystemListVersions currentVersions = buildSystemListVersions();

        record Notification(String message, Optional<Object> params) {}

        List<Notification> notifications = new ArrayList<>();

        localSessionController.computeSessionValue(sessionId, SYSTEM_LIST_VERSIONS, maybePreviousVersions -> {
            SystemListVersions previousVersions = maybePreviousVersions.orElseGet(() -> {
                log.warn("No current versions found for session %s", sessionId);
                return buildSystemListVersions();
            });

            if (!previousVersions.toolsVersion().equals(currentVersions.toolsVersion())) {
                notifications.add(new Notification(NOTIFICATION_TOOLS_LIST_CHANGED, Optional.empty()));
            }
            if (!previousVersions.promptsVersion().equals(currentVersions.promptsVersion())) {
                notifications.add(new Notification(NOTIFICATION_PROMPTS_LIST_CHANGED, Optional.empty()));
            }
            if (!previousVersions.resourcesVersion().equals(currentVersions.resourcesVersion()) || !previousVersions.resourceTemplatesVersion().equals(currentVersions.resourceTemplatesVersion())) {
                notifications.add(new Notification(NOTIFICATION_RESOURCES_LIST_CHANGED, Optional.empty()));
            }

            return Optional.of(currentVersions);
        });

        // iterate over any subscribed resources and notify if updated
        //
        // TODO this is somewhat expensive. In the future consider adding some hooks that clients can use to optimize this.
        Optional<String> cursor = Optional.empty();
        do {
            List<Map.Entry<String, ResourceVersion>> subscriptions = localSessionController.listSessionValues(sessionId, ResourceVersion.class, RECONCILE_PAGE_SIZE, cursor);
            subscriptions.forEach(subscription -> {
                String uri = subscription.getKey();

                Optional<List<ResourceContents>> resourceContents = internalReadResource(new ReadResourceRequest(uri, Optional.empty()), requestContext);
                String newHash = resourceContents
                        .map(contents -> listHash(contents.stream()))
                        .orElse("");

                localSessionController.computeSessionValue(sessionId, resourceVersionKey(uri), maybeOldVersion -> {
                    if (maybeOldVersion.map(oldVersion -> !oldVersion.version().equals(newHash)).orElse(false)) {
                        notifications.add(new Notification(NOTIFICATION_RESOURCES_UPDATED, Optional.of(new ResourcesUpdatedNotification(uri))));
                    }
                    return Optional.of(new ResourceVersion(newHash));
                });
            });

            cursor = (subscriptions.size() < RECONCILE_PAGE_SIZE) ? Optional.empty() : Optional.of(subscriptions.getLast().getKey());
        }
        while (cursor.isPresent());

        // ideally, we'd send these messages before updating the session state, but that would require
        // sending them inside of a DB transaction and that isn't ideal. So, we send them after updating the session state.
        // There is a small chance that these messages fail to send and the client will miss the notifications.
        notifications.forEach(notification -> requestContext.sendMessage(notification.message, notification.params));
    }

    ListTasksResult listTasks(HttpServletRequest request, InternalMessageWriter messageWriter, ListRequest listRequest)
    {
        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, Optional.empty(), taskController);

        List<Task> tasks = requestContext.tasks().listTasks(paginationUtil.pageSize(), listRequest.cursor())
                .stream()
                .map(TaskFacade::toTask)
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, tasks, Task::taskId, ListTasksResult::new);
    }

    Task getTask(HttpServletRequest request, InternalMessageWriter messageWriter, GetTaskRequest getTaskRequest)
    {
        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, Optional.of(getTaskRequest), taskController);

        return requestContext.tasks().getTask(getTaskRequest.taskId())
                .map(TaskFacade::toTask)
                .orElseThrow(() -> exception(INVALID_PARAMS, "Task not found: " + getTaskRequest.taskId()));
    }

    JsonRpcMessage blockUntilTaskCompletion(HttpServletRequest request, InternalMessageWriter messageWriter, GetTaskRequest getTaskRequest)
    {
        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, Optional.empty(), taskController);

        Duration timeoutRemaining = Duration.from(timeout);

        String taskId = getTaskRequest.taskId();

        JsonRpcMessage completionResult = null;
        try {
            while ((completionResult == null) && timeoutRemaining.isPositive()) {
                Stopwatch stopwatch = Stopwatch.createStarted();

                TaskFacade taskFacade = requestContext.tasks().blockUntil(taskId, pingThreshold, timeoutRemaining, hasMessage.or(isCompleted));
                completionResult = switch (taskFacade.toTaskStatus()) {
                    case INPUT_REQUIRED -> {
                        JsonRpcMessage message = taskFacade.message().orElseThrow(() -> exception(INTERNAL_ERROR, "Task completed without a response. Task ID: " + taskId));
                        messageWriter.writeMessage(objectMapper.writeValueAsString(message));
                        messageWriter.flushMessages();
                        requestContext.tasks().clearTaskMessages(taskId);
                        yield null;
                    }

                    case COMPLETED, CANCELLED, FAILED -> taskFacade.message().orElseThrow(() -> exception(INTERNAL_ERROR, "Task completed without a response. Task ID: " + taskId));

                    default -> null;
                };

                timeoutRemaining = timeoutRemaining.minus(stopwatch.elapsed());
            }

            if (completionResult == null) {
                throw new TimeoutException();
            }

            return completionResult;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(INTERNAL_ERROR, "Task interrupted while waiting for task result. Task ID: " + taskId);
        }
        catch (IOException e) {
            throw exception(INTERNAL_ERROR, "I/O error while sending task result. Task ID: " + taskId);
        }
        catch (TimeoutException e) {
            throw exception(REQUEST_TIMEOUT, "Timed out waiting for task result. Task ID: " + taskId);
        }
    }

    Task blockUntilTaskCancelled(HttpServletRequest request, InternalMessageWriter messageWriter, GetTaskRequest getTaskRequest)
    {
        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, Optional.empty(), taskController);

        requestContext.tasks().requestTaskCancellation(getTaskRequest.taskId(), Optional.empty());
        try {
            return requestContext.tasks().blockUntil(getTaskRequest.taskId(), pingThreshold, timeout, isCompleted).toTask();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(INTERNAL_ERROR, "Task interrupted while waiting for task cancellation. Task ID: " + getTaskRequest.taskId());
        }
        catch (TimeoutException e) {
            throw exception(REQUEST_TIMEOUT, "Timed out waiting for task cancellation0. Task ID: " + getTaskRequest.taskId());
        }
    }

    void acceptTaskResponse(HttpServletRequest request, InternalMessageWriter messageWriter, String taskId, JsonRpcResponse<?> rpcResponse)
    {
        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, Optional.empty(), taskController);

        requestContext.tasks().addServerToClientResponse(taskId, rpcResponse);
    }

    private SystemListVersions buildSystemListVersions()
    {
        return new SystemListVersions(
                listHash(tools.values().stream().map(ToolEntry::tool)),
                listHash(prompts.values().stream().map(PromptEntry::prompt)),
                listHash(resources.values().stream().map(ResourceEntry::resource)),
                listHash(resourceTemplates.values().stream().map(ResourceTemplateEntry::resourceTemplate)));
    }

    private Optional<List<ResourceContents>> internalReadResource(ReadResourceRequest readResourceRequest, McpRequestContext requestContext)
    {
        return findResource(readResourceRequest.uri())
                .map(resourceEntry -> resourceEntry.handler().readResource(requestContext, resourceEntry.resource(), readResourceRequest))
                .or(() -> findResourceTemplate(readResourceRequest.uri()).map(match -> match.entry.handler().readResourceTemplate(requestContext, match.entry.resourceTemplate(), readResourceRequest, match.values)));
    }

    private SessionController requireSessionController()
    {
        return sessionController.orElseThrow(() -> exception(INVALID_REQUEST, "Sessions are not enabled"));
    }

    private TaskController requireTaskController()
    {
        return taskController.orElseThrow(() -> exception(INVALID_REQUEST, "Tasks are not enabled"));
    }

    private Optional<ResourceEntry> findResource(String uriString)
    {
        URI uri = toUri(uriString);

        return resources.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(uri))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private record ResourceTemplateMatch(ResourceTemplateEntry entry, ResourceTemplateValues values) {}

    private Optional<ResourceTemplateMatch> findResourceTemplate(String uri)
    {
        return resourceTemplates.entrySet()
                .stream()
                .flatMap(entry -> {
                    UriTemplate uriTemplate = entry.getKey();
                    Map<String, String> variables = new HashMap<>();
                    if (uriTemplate.match(uri, variables)) {
                        return Stream.of(new ResourceTemplateMatch(entry.getValue(), new ResourceTemplateValues(variables)));
                    }
                    return Stream.of();
                })
                .findFirst();
    }

    private Optional<Object> progressToken(Meta meta)
    {
        return meta.meta().flatMap(m -> Optional.ofNullable(m.get(PROGRESS_TOKEN)));
    }

    private static URI toUri(String uriString)
    {
        try {
            return URI.create(uriString);
        }
        catch (IllegalArgumentException e) {
            throw exception(INVALID_REQUEST, "Invalid URI: " + uriString);
        }
    }

    private static UriTemplate toUriTemplate(String uriString)
    {
        try {
            return new UriTemplate(uriString);
        }
        catch (IllegalArgumentException e) {
            throw exception(INVALID_REQUEST, "Invalid URI: " + uriString);
        }
    }

    private static String completionKey(CompleteReference reference)
    {
        return switch (reference) {
            case PromptReference(var name, _) -> name;
            case ResourceReference(var uri) -> uri;
        };
    }

    @SuppressWarnings("UnstableApiUsage")
    private <T> String listHash(Stream<? extends T> stream)
    {
        Hasher hasher = Hashing.sha256().newHasher();
        stream.map(this::asJson)
                .forEach(json -> hasher.putString(json, UTF_8));
        return hasher.hash().toString();
    }

    private <T> String asJson(T item)
    {
        try {
            return objectMapper.writeValueAsString(item);
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }
}
