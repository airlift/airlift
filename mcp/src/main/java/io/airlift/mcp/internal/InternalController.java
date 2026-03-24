package io.airlift.mcp.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import io.airlift.mcp.McpCapabilityFilter;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.CompletionEntry;
import io.airlift.mcp.handler.CompletionHandler;
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
import io.airlift.mcp.model.Implementation;
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
import io.airlift.mcp.model.SetLevelRequest;
import io.airlift.mcp.model.SubscribeListChanged;
import io.airlift.mcp.model.SubscribeRequest;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.reflection.IconHelper;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.versions.VersionsController;
import org.glassfish.jersey.uri.UriTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.http.server.tracing.TracingServletFilter.updateRequestSpan;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpModule.MCP_SERVER_ICONS;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.RESOURCE_NOT_FOUND;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.airlift.mcp.sessions.SessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.sessions.SessionValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.sessions.SessionValueKey.PROTOCOL;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_RESOURCE_URI;
import static java.util.Objects.requireNonNull;

public class InternalController
        implements McpEntities
{
    private final Map<String, ToolEntry> tools = new ConcurrentHashMap<>();
    private final Map<String, PromptEntry> prompts = new ConcurrentHashMap<>();
    private final Map<URI, ResourceEntry> resources = new ConcurrentHashMap<>();
    private final Map<UriTemplate, ResourceTemplateEntry> resourceTemplates = new ConcurrentHashMap<>();
    private final Map<String, CompletionEntry> completions = new ConcurrentHashMap<>();
    private final McpMetadata metadata;
    private final PaginationUtil paginationUtil;
    private final Optional<SessionController> sessionController;
    private final Provider<VersionsController> versionsController;
    private final McpCapabilityFilter capabilityFilter;
    private final Duration sessionTimeout;
    private final Implementation serverImplementation;

    @Inject
    InternalController(
            McpMetadata metadata,
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
            Provider<VersionsController> versionsController,
            McpCapabilityFilter capabilityFilter)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.paginationUtil = requireNonNull(paginationUtil, "paginationUtil is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.versionsController = requireNonNull(versionsController, "versionsController is null");
        this.capabilityFilter = requireNonNull(capabilityFilter, "capabilityFilter is null");

        tools.forEach(tool -> addTool(tool.tool(), tool.toolHandler()));
        prompts.forEach(prompt -> addPrompt(prompt.prompt(), prompt.promptHandler()));
        resources.forEach(resource -> addResource(resource.resource(), resource.handler()));
        resourceTemplates.forEach(resourceTemplate -> addResourceTemplate(resourceTemplate.resourceTemplate(), resourceTemplate.handler()));
        completions.forEach(completion -> addCompletion(completion.reference(), completion.handler()));

        sessionTimeout = mcpConfig.getDefaultSessionTimeout().toJavaTime();

        serverImplementation = iconHelper.mapIcons(serverIcons).map(icons -> metadata.implementation().withAdditionalIcons(icons))
                .orElse(metadata.implementation());
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

    @Override
    public List<Tool> tools(McpRequestContext requestContext)
    {
        return tools.values()
                .stream()
                .map(ToolEntry::tool)
                .filter(tool -> capabilityFilter.isAllowed(requestContext.identity(), tool))
                .collect(toImmutableList());
    }

    @Override
    public List<Prompt> prompts(McpRequestContext requestContext)
    {
        return prompts.values()
                .stream()
                .map(PromptEntry::prompt)
                .filter(prompt -> capabilityFilter.isAllowed(requestContext.identity(), prompt))
                .collect(toImmutableList());
    }

    @Override
    public List<Resource> resources(McpRequestContext requestContext)
    {
        return resources.values()
                .stream()
                .map(ResourceEntry::resource)
                .filter(resource -> capabilityFilter.isAllowed(requestContext.identity(), resource))
                .collect(toImmutableList());
    }

    @Override
    public List<ResourceTemplate> resourceTemplates(McpRequestContext requestContext)
    {
        return resourceTemplates.values()
                .stream()
                .map(ResourceTemplateEntry::resourceTemplate)
                .filter(resourceTemplate -> capabilityFilter.isAllowed(requestContext.identity(), resourceTemplate))
                .collect(toImmutableList());
    }

    @Override
    public List<CompleteReference> completions(McpRequestContext requestContext)
    {
        return completions.values()
                .stream()
                .map(CompletionEntry::reference)
                .filter(completeReference -> capabilityFilter.isAllowed(requestContext.identity(), completeReference))
                .collect(toImmutableList());
    }

    @Override
    public Optional<List<ResourceContents>> readResourceContents(McpRequestContext requestContext, ReadResourceRequest readResourceRequest)
    {
        if (!capabilityFilter.isAllowed(requestContext.identity(), readResourceRequest.uri())) {
            throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + readResourceRequest.uri()));
        }

        URI uri = URI.create(readResourceRequest.uri());
        ResourceEntry resourceEntry = resources.get(uri);
        if (resourceEntry != null) {
            if (!capabilityFilter.isAllowed(requestContext.identity(), resourceEntry.resource())) {
                throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + readResourceRequest.uri()));
            }
        }

        for (Map.Entry<UriTemplate, ResourceTemplateEntry> entry : resourceTemplates.entrySet()) {
            if (entry.getKey().match(readResourceRequest.uri(), new HashMap<>())) {
                if (!capabilityFilter.isAllowed(requestContext.identity(), entry.getValue().resourceTemplate())) {
                    throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + readResourceRequest.uri()));
                }
                break;
            }
        }

        return findResource(readResourceRequest.uri())
                .map(readResourceEntry -> readResourceEntry.handler().readResource(requestContext, readResourceEntry.resource(), readResourceRequest))
                .or(() -> findResourceTemplate(readResourceRequest.uri()).map(match -> match.entry.handler().readResourceTemplate(requestContext, match.entry.resourceTemplate(), readResourceRequest, match.values)));
    }

    InitializeResult initialize(InternalRequestContext requestContext, InitializeRequest initializeRequest)
    {
        Protocol protocol = Protocol.of(initializeRequest.protocolVersion())
                .orElse(LATEST_PROTOCOL);

        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_PROTOCOL_VERSION, protocol.value()));

        boolean sessionsEnabled = sessionController.map(controller -> {
            SessionId sessionId = controller.createSession(requestContext.identity(), Optional.of(sessionTimeout));
            InternalRequestContext localRequestContext = requestContext.withSessonId(sessionId);

            localRequestContext.response().addHeader(MCP_SESSION_ID, sessionId.id());

            versionsController.get().initializeSessionVersions(localRequestContext);

            controller.setSessionValue(sessionId, LOGGING_LEVEL, LoggingLevel.INFO);
            controller.setSessionValue(sessionId, CLIENT_CAPABILITIES, initializeRequest.capabilities());
            controller.setSessionValue(sessionId, PROTOCOL, protocol);

            updateRequestSpan(localRequestContext.request(), span -> span.setAttribute(MCP_SESSION_ID, sessionId.id()));

            return true;
        }).orElse(false);

        ServerCapabilities serverCapabilities = new ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new CompletionCapabilities()),
                sessionsEnabled ? Optional.of(new LoggingCapabilities()) : Optional.empty(),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)),
                resources.isEmpty() && resourceTemplates.isEmpty() ? Optional.empty() : Optional.of(new SubscribeListChanged(sessionsEnabled, sessionsEnabled)),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)),
                Optional.empty());

        Implementation localImplementation = protocol.supportsIcons() ? serverImplementation : serverImplementation.simpleForm();

        return new InitializeResult(protocol.value(), serverCapabilities, localImplementation, metadata.instructions());
    }

    ListToolsResult listTools(InternalRequestContext requestContext, ListRequest listRequest)
    {
        List<Tool> localTools = tools.values().stream()
                .map(ToolEntry::tool)
                .filter(tool -> capabilityFilter.isAllowed(requestContext.identity(), tool))
                .map(tool -> requestContext.protocol().supportsIcons() ? tool : tool.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localTools, Tool::name, ListToolsResult::new);
    }

    ListPromptsResult listPrompts(InternalRequestContext requestContext, ListRequest listRequest)
    {
        List<Prompt> localPrompts = prompts.values().stream()
                .map(PromptEntry::prompt)
                .filter(prompt -> capabilityFilter.isAllowed(requestContext.identity(), prompt))
                .map(prompt -> requestContext.protocol().supportsIcons() ? prompt : prompt.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localPrompts, Prompt::name, ListPromptsResult::new);
    }

    ListResourcesResult listResources(InternalRequestContext requestContext, ListRequest listRequest)
    {
        List<Resource> localResources = resources.values().stream()
                .map(ResourceEntry::resource)
                .filter(resource -> capabilityFilter.isAllowed(requestContext.identity(), resource))
                .map(resource -> requestContext.protocol().supportsIcons() ? resource : resource.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResources, Resource::name, ListResourcesResult::new);
    }

    ListResourceTemplatesResult listResourceTemplates(InternalRequestContext requestContext, ListRequest listRequest)
    {
        List<ResourceTemplate> localResourceTemplates = resourceTemplates.values().stream()
                .map(ResourceTemplateEntry::resourceTemplate)
                .filter(resourceTemplate -> capabilityFilter.isAllowed(requestContext.identity(), resourceTemplate))
                .map(resourceTemplate -> requestContext.protocol().supportsIcons() ? resourceTemplate : resourceTemplate.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResourceTemplates, ResourceTemplate::name, ListResourceTemplatesResult::new);
    }

    CallToolResult callTool(InternalRequestContext requestContext, CallToolRequest callToolRequest)
    {
        ToolEntry toolEntry = tools.get(callToolRequest.name());
        if (toolEntry == null) {
            throw exception(INVALID_PARAMS, "Tool not found: " + callToolRequest.name());
        }

        if (!capabilityFilter.isAllowed(requestContext.identity(), toolEntry.tool())) {
            return new CallToolResult(ImmutableList.of(new TextContent("Tool not allowed: " + callToolRequest.name())), Optional.empty(), true);
        }

        try {
            return toolEntry.toolHandler().callTool(requestContext.withProgressToken(progressToken(callToolRequest)), callToolRequest);
        }
        catch (McpClientException mcpClientException) {
            return new CallToolResult(ImmutableList.of(new TextContent(mcpClientException.unwrap().errorDetail().message())), Optional.empty(), true, Optional.empty());
        }
    }

    GetPromptResult getPrompt(InternalRequestContext requestContext, GetPromptRequest getPromptRequest)
    {
        PromptEntry promptEntry = prompts.get(getPromptRequest.name());
        if (promptEntry == null) {
            throw exception(INVALID_PARAMS, "Prompt not found: " + getPromptRequest.name());
        }

        if (!capabilityFilter.isAllowed(requestContext.identity(), promptEntry.prompt())) {
            throw new McpClientException(exception(INVALID_PARAMS, "Prompt not allowed: " + getPromptRequest.name()));
        }

        return promptEntry.promptHandler().getPrompt(requestContext.withProgressToken(progressToken(getPromptRequest)), getPromptRequest);
    }

    ReadResourceResult readResources(InternalRequestContext requestContext, ReadResourceRequest readResourceRequest)
    {
        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, readResourceRequest.uri()));

        List<ResourceContents> resourceContents = readResourceContents(requestContext.withProgressToken(progressToken(readResourceRequest)), readResourceRequest)
                .orElseThrow(() -> exception(RESOURCE_NOT_FOUND, "Resource not found: " + readResourceRequest.uri()));

        return new ReadResourceResult(resourceContents);
    }

    Object setLoggingLevel(InternalRequestContext requestContext, SetLevelRequest setLevelRequest)
    {
        requestContext.session().setValue(LOGGING_LEVEL, setLevelRequest.level());
        return ImmutableMap.of();
    }

    CompleteResult completionComplete(InternalRequestContext requestContext, CompleteRequest completeRequest)
    {
        CompletionEntry completionEntry = completions.get(completionKey(completeRequest.ref()));
        if (completionEntry == null) {
            return new CompleteResult(new CompleteCompletion(ImmutableList.of(), OptionalInt.empty(), OptionalBoolean.UNDEFINED));
        }

        if (!capabilityFilter.isAllowed(requestContext.identity(), completionEntry.reference())) {
            return new CompleteResult(new CompleteCompletion(ImmutableList.of(), OptionalInt.empty(), OptionalBoolean.UNDEFINED));
        }

        return completionEntry.handler().complete(requestContext.withProgressToken(progressToken(completeRequest)), completeRequest);
    }

    Object resourcesSubscribe(InternalRequestContext requestContext, SubscribeRequest subscribeRequest)
    {
        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, subscribeRequest.uri()));

        if (!capabilityFilter.isAllowed(requestContext.identity(), subscribeRequest.uri())) {
            throw new McpClientException(exception(INVALID_PARAMS, "Resource access not allowed: " + subscribeRequest.uri()));
        }

        versionsController.get().resourcesSubscribe(requestContext.withProgressToken(progressToken(subscribeRequest)), subscribeRequest);

        return ImmutableMap.of();
    }

    Object resourcesUnsubscribe(InternalRequestContext requestContext, SubscribeRequest subscribeRequest)
    {
        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, subscribeRequest.uri()));

        versionsController.get().resourcesUnsubscribe(requestContext, subscribeRequest.uri());

        return ImmutableMap.of();
    }

    void reconcileVersions(InternalRequestContext requestContext)
    {
        versionsController.get().reconcileVersions(requestContext);
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
}
