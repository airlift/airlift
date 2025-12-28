package io.airlift.mcp.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import io.airlift.bootstrap.LifeCycleManager;
import io.airlift.mcp.McpConfig;
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
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.InitializeResult.CompletionCapabilities;
import io.airlift.mcp.model.InitializeResult.LoggingCapabilities;
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.McpIdentity;
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
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import io.airlift.mcp.versions.ResourceVersion;
import io.airlift.mcp.versions.SystemListVersions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.glassfish.jersey.uri.UriTemplate;

import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.internal.InternalRequestContext.requireSessionId;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_RESOURCES_UPDATED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.RESOURCE_NOT_FOUND;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.sessions.SessionValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.sessions.SessionValueKey.PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.SYSTEM_LIST_VERSIONS;
import static io.airlift.mcp.sessions.SessionValueKey.resourceVersionKey;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class InternalMcpServer
        implements McpServer
{
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
    private final Duration versionUpdateInterval;

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
            McpConfig mcpConfig)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.lifeCycleManager = requireNonNull(lifeCycleManager, "lifeCycleManager is null");
        this.paginationUtil = requireNonNull(paginationUtil, "paginationUtil is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");

        tools.forEach(tool -> addTool(tool.tool(), tool.toolHandler()));
        prompts.forEach(prompt -> addPrompt(prompt.prompt(), prompt.promptHandler()));
        resources.forEach(resource -> addResource(resource.resource(), resource.handler()));
        resourceTemplates.forEach(resourceTemplate -> addResourceTemplate(resourceTemplate.resourceTemplate(), resourceTemplate.handler()));
        completions.forEach(completion -> addCompletion(completion.reference(), completion.handler()));

        sessionTimeout = mcpConfig.getDefaultSessionTimeout().toJavaTime();
        versionUpdateInterval = mcpConfig.getResourceVersionUpdateInterval().toJavaTime();
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

    InitializeResult initialize(HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, InitializeRequest initializeRequest)
    {
        Protocol protocol = Protocol.of(initializeRequest.protocolVersion())
                .orElse(LATEST_PROTOCOL);

        boolean sessionsEnabled = sessionController.map(controller -> {
            SessionId sessionId = controller.createSession(authenticated, Optional.of(sessionTimeout));
            response.addHeader(MCP_SESSION_ID, sessionId.id());

            controller.setSessionValue(sessionId, LOGGING_LEVEL, LoggingLevel.INFO);
            controller.setSessionValue(sessionId, SYSTEM_LIST_VERSIONS, buildSystemListVersions());
            controller.setSessionValue(sessionId, CLIENT_CAPABILITIES, initializeRequest.capabilities());
            controller.setSessionValue(sessionId, PROTOCOL, protocol);

            return true;
        }).orElse(false);

        ServerCapabilities serverCapabilities = new ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new CompletionCapabilities()),
                sessionsEnabled ? Optional.of(new LoggingCapabilities()) : Optional.empty(),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)),
                resources.isEmpty() ? Optional.empty() : Optional.of(new SubscribeListChanged(sessionsEnabled, sessionsEnabled)),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)));

        return new InitializeResult(protocol.value(), serverCapabilities, metadata.implementation(), metadata.instructions());
    }

    ListToolsResult listTools(ListRequest listRequest)
    {
        List<Tool> localTools = tools.values().stream()
                .map(ToolEntry::tool)
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localTools, Tool::name, ListToolsResult::new);
    }

    ListPromptsResult listPrompts(ListRequest listRequest)
    {
        List<Prompt> localPrompts = prompts.values().stream()
                .map(PromptEntry::prompt)
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localPrompts, Prompt::name, ListPromptsResult::new);
    }

    ListResourcesResult listResources(ListRequest listRequest)
    {
        List<Resource> localResources = resources.values().stream()
                .map(ResourceEntry::resource)
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResources, Resource::name, ListResourcesResult::new);
    }

    ListResourceTemplatesResult listResourceTemplates(ListRequest listRequest)
    {
        List<ResourceTemplate> localResourceTemplates = resourceTemplates.values().stream()
                .map(ResourceTemplateEntry::resourceTemplate)
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResourceTemplates, ResourceTemplate::name, ListResourceTemplatesResult::new);
    }

    CallToolResult callTool(HttpServletRequest request, MessageWriter messageWriter, CallToolRequest callToolRequest)
    {
        ToolEntry toolEntry = tools.get(callToolRequest.name());
        if (toolEntry == null) {
            throw exception(INVALID_PARAMS, "Tool not found: " + callToolRequest.name());
        }

        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(callToolRequest));
        return toolEntry.toolHandler().callTool(requestContext, callToolRequest);
    }

    GetPromptResult getPrompt(HttpServletRequest request, MessageWriter messageWriter, GetPromptRequest getPromptRequest)
    {
        PromptEntry promptEntry = prompts.get(getPromptRequest.name());
        if (promptEntry == null) {
            throw exception(INVALID_PARAMS, "Prompt not found: " + getPromptRequest.name());
        }

        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(getPromptRequest));
        return promptEntry.promptHandler().getPrompt(requestContext, getPromptRequest);
    }

    ReadResourceResult readResources(HttpServletRequest request, MessageWriter messageWriter, ReadResourceRequest readResourceRequest)
    {
        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(readResourceRequest));

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

        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(completeRequest));

        return completionEntry.handler().complete(requestContext, completeRequest);
    }

    Object resourcesSubscribe(HttpServletRequest request, InternalMessageWriter messageWriter, SubscribeRequest subscribeRequest)
    {
        SessionController localSessionController = requireSessionController();
        SessionId sessionId = requireSessionId(request);
        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, progressToken(subscribeRequest));

        List<ResourceContents> resourceContents = internalReadResource(new ReadResourceRequest(subscribeRequest.uri(), subscribeRequest.meta()), requestContext)
                .orElseThrow(() -> exception(RESOURCE_NOT_FOUND, "Resource not found: " + subscribeRequest.uri()));

        String hash = listHash(resourceContents.stream());
        SessionValueKey<ResourceVersion> key = resourceVersionKey(subscribeRequest.uri());

        localSessionController.setSessionValue(sessionId, key, new ResourceVersion(hash));

        return ImmutableMap.of();
    }

    Object resourcesUnsubscribe(HttpServletRequest request, SubscribeRequest subscribeRequest)
    {
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

        Instant now = Instant.now();

        SystemListVersions sessionSystemListVersions = localSessionController.getSessionValue(sessionId, SYSTEM_LIST_VERSIONS).orElse(new SystemListVersions("", "", "", "", now));

        Duration elapsedSinceLastCheck = Duration.between(sessionSystemListVersions.lastCheck(), now);
        if (elapsedSinceLastCheck.compareTo(versionUpdateInterval) < 0) {
            return;
        }

        SystemListVersions systemVersions = buildSystemListVersions();

        McpRequestContext requestContext = new InternalRequestContext(objectMapper, sessionController, request, messageWriter, Optional.empty());

        if (!sessionSystemListVersions.toolsVersion().equals(systemVersions.toolsVersion())) {
            requestContext.sendMessage(NOTIFICATION_TOOLS_LIST_CHANGED, Optional.empty());
        }
        if (!sessionSystemListVersions.promptsVersion().equals(systemVersions.promptsVersion())) {
            requestContext.sendMessage(NOTIFICATION_PROMPTS_LIST_CHANGED, Optional.empty());
        }
        if (!sessionSystemListVersions.resourcesVersion().equals(systemVersions.resourcesVersion()) || !sessionSystemListVersions.resourceTemplatesVersion().equals(systemVersions.resourceTemplatesVersion())) {
            requestContext.sendMessage(NOTIFICATION_RESOURCES_LIST_CHANGED, Optional.empty());
        }

        localSessionController.setSessionValue(sessionId, SYSTEM_LIST_VERSIONS, systemVersions);

        // iterate over any subscribed resources and notify if updated
        Optional<String> cursor = Optional.empty();
        do {
            List<Map.Entry<String, ResourceVersion>> subscriptions = localSessionController.listSessionValues(sessionId, ResourceVersion.class, RECONCILE_PAGE_SIZE, cursor);
            subscriptions.forEach(subscription -> {
                String uri = subscription.getKey();
                ResourceVersion resourceVersion = subscription.getValue();

                Optional<List<ResourceContents>> resourceContents = internalReadResource(new ReadResourceRequest(uri, Optional.empty()), requestContext);
                String newHash = resourceContents
                        .map(contents -> listHash(contents.stream()))
                        .orElse("");
                if (!resourceVersion.version().equals(newHash)) {
                    requestContext.sendMessage(NOTIFICATION_RESOURCES_UPDATED, Optional.of(new ResourcesUpdatedNotification(uri)));
                    localSessionController.setSessionValue(sessionId, resourceVersionKey(uri), new ResourceVersion(newHash));
                }
            });

            cursor = (subscriptions.size() < RECONCILE_PAGE_SIZE) ? Optional.empty() : Optional.of(subscriptions.getLast().getKey());
        }
        while (cursor.isPresent());
    }

    private SystemListVersions buildSystemListVersions()
    {
        return new SystemListVersions(
                listHash(tools.values().stream().map(ToolEntry::tool)),
                listHash(prompts.values().stream().map(PromptEntry::prompt)),
                listHash(resources.values().stream().map(ResourceEntry::resource)),
                listHash(resourceTemplates.values().stream().map(ResourceTemplateEntry::resourceTemplate)),
                Instant.now());
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
        return meta.meta().flatMap(m -> Optional.ofNullable(m.get("progressToken")));
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
