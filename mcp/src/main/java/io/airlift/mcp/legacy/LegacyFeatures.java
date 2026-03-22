package io.airlift.mcp.legacy;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.features.Features;
import io.airlift.mcp.features.PaginationUtil;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.legacy.sessions.LegacySession;
import io.airlift.mcp.legacy.sessions.LegacySessionController;
import io.airlift.mcp.legacy.sessions.LegacySessionId;
import io.airlift.mcp.legacy.sessions.LegacySessionValueKey;
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
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
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
import io.airlift.mcp.versions.VersionsController;
import jakarta.ws.rs.WebApplicationException;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.http.server.tracing.TracingServletFilter.updateRequestSpan;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.internal.InternalFilter.MCP_RESOURCE_URI;
import static io.airlift.mcp.legacy.sessions.LegacySessionController.optionalSessionId;
import static io.airlift.mcp.legacy.sessions.LegacySessionController.requireSessionId;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.PROTOCOL;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.ROOTS;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.cancellationKey;
import static io.airlift.mcp.legacy.sessions.LegacySessionValueKey.serverToClientResponseKey;
import static io.airlift.mcp.model.Constants.MCP_SESSION_ID;
import static io.airlift.mcp.model.JsonRpcErrorCode.RESOURCE_NOT_FOUND;
import static io.airlift.mcp.model.LoggingLevel.INFO;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_PROTOCOL_VERSION;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static java.util.Objects.requireNonNull;

public class LegacyFeatures
        implements Features, LegacyEventStreaming
{
    private final LegacySessionController sessionController;
    private final VersionsController versionsController;
    private final McpEntities entities;
    private final McpMetadata metadata;
    private final PaginationUtil paginationUtil;
    private final LegacyEventStreaming legacyEventStreaming;

    @Inject
    public LegacyFeatures(
            LegacySessionController sessionController,
            VersionsController versionsController,
            McpEntities entities,
            McpMetadata metadata,
            McpConfig mcpConfig,
            LegacyEventStreaming legacyEventStreaming)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.versionsController = requireNonNull(versionsController, "versionsController is null");
        this.entities = requireNonNull(entities, "entities is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.legacyEventStreaming = requireNonNull(legacyEventStreaming, "legacyEventStreaming is null");

        paginationUtil = new PaginationUtil(mcpConfig);
    }

    @Override
    public boolean sessionEnabled()
    {
        return false;
    }

    @Override
    public InitializeResult initialize(McpRequestContext requestContext, InitializeRequest initializeRequest)
    {
        Protocol protocol = Protocol.of(initializeRequest.protocolVersion())
                .orElse(LATEST_PROTOCOL);

        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_PROTOCOL_VERSION, protocol.value()));

        LegacySession session = sessionController.createSession();
        McpRequestContext localRequestContext = requestContext.withSession(session);

        localRequestContext.response().addHeader(MCP_SESSION_ID, session.sessionId().id());

        versionsController.initializeSessionVersions(localRequestContext, session);

        session.setValue(LOGGING_LEVEL, INFO);
        session.setValue(CLIENT_CAPABILITIES, initializeRequest.capabilities());
        session.setValue(PROTOCOL, protocol);

        updateRequestSpan(localRequestContext.request(), span -> span.setAttribute(MCP_SESSION_ID, session.sessionId().id()));

        boolean sessionsEnabled = true; // TODO

        List<CompleteReference> completions = entities.completions(requestContext);
        List<Prompt> prompts = entities.prompts(requestContext);
        List<Resource> resources = entities.resources(requestContext);
        List<ResourceTemplate> resourceTemplates = entities.resourceTemplates(requestContext);
        List<Tool> tools = entities.tools(requestContext);
        Implementation serverImplementation = entities.serverImplementation();

        InitializeResult.ServerCapabilities serverCapabilities = new InitializeResult.ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new InitializeResult.CompletionCapabilities()),
                sessionsEnabled ? Optional.of(new InitializeResult.LoggingCapabilities()) : Optional.empty(),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)),
                resources.isEmpty() && resourceTemplates.isEmpty() ? Optional.empty() : Optional.of(new SubscribeListChanged(sessionsEnabled, sessionsEnabled)),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(sessionsEnabled)),
                Optional.empty());

        Implementation localImplementation = protocol.supportsIcons() ? serverImplementation : serverImplementation.simpleForm();

        return new InitializeResult(protocol.value(), serverCapabilities, localImplementation, metadata.instructions());
    }

    @Override
    public ListToolsResult listTools(McpRequestContext requestContext, ListRequest listRequest)
    {
        requireSession(requestContext);

        List<Tool> localTools = entities.tools(requestContext)
                .stream()
                .map(tool -> requestContext.protocol().supportsIcons() ? tool : tool.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localTools, Tool::name, ListToolsResult::new);
    }

    @Override
    public ListPromptsResult listPrompts(McpRequestContext requestContext, ListRequest listRequest)
    {
        requireSession(requestContext);

        List<Prompt> localPrompts = entities.prompts(requestContext)
                .stream()
                .map(prompt -> requestContext.protocol().supportsIcons() ? prompt : prompt.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localPrompts, Prompt::name, ListPromptsResult::new);
    }

    @Override
    public ListResourcesResult listResources(McpRequestContext requestContext, ListRequest listRequest)
    {
        requireSession(requestContext);

        List<Resource> localResources = entities.resources(requestContext)
                .stream()
                .map(resource -> requestContext.protocol().supportsIcons() ? resource : resource.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResources, Resource::name, ListResourcesResult::new);
    }

    @Override
    public ListResourceTemplatesResult listResourceTemplates(McpRequestContext requestContext, ListRequest listRequest)
    {
        requireSession(requestContext);

        List<ResourceTemplate> localResourceTemplates = entities.resourceTemplates(requestContext)
                .stream()
                .map(resourceTemplate -> requestContext.protocol().supportsIcons() ? resourceTemplate : resourceTemplate.withoutIcons())
                .collect(toImmutableList());
        return paginationUtil.paginate(listRequest, localResourceTemplates, ResourceTemplate::name, ListResourceTemplatesResult::new);
    }

    @Override
    public CallToolResult callTool(McpRequestContext requestContext, CallToolRequest callToolRequest)
    {
        requireSession(requestContext);

        ToolEntry toolEntry = entities.requireTool(requestContext, callToolRequest.name());

        try {
            return toolEntry.toolHandler().callTool(requestContext.withProgressToken(progressToken(callToolRequest)), callToolRequest);
        }
        catch (McpClientException mcpClientException) {
            return new CallToolResult(ImmutableList.of(new Content.TextContent(mcpClientException.unwrap().errorDetail().message())), Optional.empty(), true, Optional.empty());
        }
    }

    @Override
    public GetPromptResult getPrompt(McpRequestContext requestContext, GetPromptRequest getPromptRequest)
    {
        requireSession(requestContext);

        PromptEntry promptEntry = entities.requirePrompt(requestContext, getPromptRequest.name());
        return promptEntry.promptHandler().getPrompt(requestContext.withProgressToken(progressToken(getPromptRequest)), getPromptRequest);
    }

    @Override
    public ReadResourceResult readResources(McpRequestContext requestContext, ReadResourceRequest readResourceRequest)
    {
        requireSession(requestContext);

        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, readResourceRequest.uri()));

        List<ResourceContents> resourceContents = entities.readResourceContents(requestContext.withProgressToken(progressToken(readResourceRequest)), readResourceRequest)
                .orElseThrow(() -> exception(RESOURCE_NOT_FOUND, "Resource not found: " + readResourceRequest.uri()));

        return new ReadResourceResult(resourceContents);
    }

    @Override
    public void setLoggingLevel(McpRequestContext requestContext, SetLevelRequest setLevelRequest)
    {
        LegacySession session = requireSession(requestContext);
        session.setValue(LOGGING_LEVEL, setLevelRequest.level());
    }

    @Override
    public CompleteResult completionComplete(McpRequestContext requestContext, CompleteRequest completeRequest)
    {
        requireSession(requestContext);

        return entities.completion(requestContext, completeRequest.ref())
                .map(completionEntry -> completionEntry.handler().complete(requestContext.withProgressToken(progressToken(completeRequest)), completeRequest))
                .orElseGet(() -> new CompleteResult(new CompleteResult.CompleteCompletion(ImmutableList.of(), OptionalInt.empty(), OptionalBoolean.UNDEFINED)));
    }

    @Override
    public void resourcesSubscribe(McpRequestContext requestContext, SubscribeRequest subscribeRequest)
    {
        LegacySession session = requireSession(requestContext);

        entities.requireResource(requestContext, subscribeRequest.uri());

        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, subscribeRequest.uri()));

        versionsController.resourcesSubscribe(requestContext.withProgressToken(progressToken(subscribeRequest)), session, subscribeRequest);
    }

    @Override
    public void resourcesUnsubscribe(McpRequestContext requestContext, SubscribeRequest subscribeRequest)
    {
        LegacySession session = requireSession(requestContext);

        entities.requireResource(requestContext, subscribeRequest.uri());

        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, subscribeRequest.uri()));

        versionsController.resourcesUnsubscribe(session, subscribeRequest.uri());
    }

    @Override
    public void reconcileVersions(McpRequestContext requestContext)
    {
        LegacySession session = requireSession(requestContext);

        versionsController.reconcileVersions(requestContext, session);
    }

    @Override
    public void acceptCancellation(McpRequestContext requestContext, CancelledNotification cancelledNotification)
    {
        LegacySession session = requireSession(requestContext);

        LegacySessionValueKey<CancelledNotification> cancellationKey = cancellationKey(cancelledNotification.requestId());
        session.setValue(cancellationKey, cancelledNotification);
    }

    @Override
    public void acceptRootsChanged(McpRequestContext requestContext)
    {
        LegacySession session = requireSession(requestContext);

        session.deleteValue(ROOTS);
    }

    @Override
    public void acceptResponse(McpRequestContext requestContext, JsonRpcResponse<?> rpcResponse)
    {
        optionalSessionId(requestContext.request())
                .flatMap(sessionController::session)
                .ifPresent(session -> session.setValue(serverToClientResponseKey(rpcResponse.id()), rpcResponse));
    }

    @Override
    public void acceptSessionDelete(McpRequestContext requestContext)
    {
        optionalSessionId(requestContext.request())
                .ifPresent(sessionController::deleteSession);
    }

    @Override
    public void handleEventStreaming(McpRequestContext requestContext)
    {
        legacyEventStreaming.handleEventStreaming(requestContext);
    }

    @Override
    public void checkSaveSentMessages(McpRequestContext requestContext)
    {
        legacyEventStreaming.checkSaveSentMessages(requestContext);
    }

    private Optional<Object> progressToken(Meta meta)
    {
        return meta.meta().flatMap(m -> Optional.ofNullable(m.get("progressToken")));
    }

    private LegacySession requireSession(McpRequestContext requestContext)
    {
        LegacySessionId sessionId = requireSessionId(requestContext.request());
        return sessionController.session(sessionId)
                .orElseThrow(() -> new WebApplicationException(NOT_FOUND));
    }
}
