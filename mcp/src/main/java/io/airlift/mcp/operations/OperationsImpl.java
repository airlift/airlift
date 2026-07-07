package io.airlift.mcp.operations;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.McpMetadataMapper;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.PromptEntry;
import io.airlift.mcp.handler.ToolEntry;
import io.airlift.mcp.messages.MessageWriter;
import io.airlift.mcp.model.CacheableResult;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.CompleteResult;
import io.airlift.mcp.model.DiscoverResult;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.GetPromptResult;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeResult.CompletionCapabilities;
import io.airlift.mcp.model.InitializeResult.LoggingCapabilities;
import io.airlift.mcp.model.InitializeResult.ServerCapabilities;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.ListPromptsResult;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.ListResourceTemplatesResult;
import io.airlift.mcp.model.ListResourcesResult;
import io.airlift.mcp.model.ListToolsResult;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.ReadResourceResult;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceContents;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.SubscribeListChanged;
import io.airlift.mcp.model.SubscriptionNotifications;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.reflection.IconHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.airlift.http.server.tracing.TracingServletFilter.updateRequestSpan;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpModule.MCP_SERVER_ICONS;
import static io.airlift.mcp.model.CacheScope.PRIVATE;
import static io.airlift.mcp.model.Constants.HEADER_MCP_NAME;
import static io.airlift.mcp.model.Constants.MESSAGE_WRITER_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_GET;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_SERVER_DISCOVER;
import static io.airlift.mcp.model.Constants.METHOD_SUBSCRIPTIONS_LISTEN;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_CALL;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.JsonRpcErrorCode.HEADER_MISMATCH;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.METHOD_NOT_FOUND;
import static io.airlift.mcp.model.ResultType.COMPLETE;
import static io.airlift.mcp.operations.McpTracingAttributes.MCP_METHOD_NAME;
import static io.airlift.mcp.operations.McpTracingAttributes.MCP_PROTOCOL_VERSION;
import static io.airlift.mcp.operations.McpTracingAttributes.MCP_RESOURCE_URI;
import static io.airlift.mcp.operations.Operations.convertParams;
import static io.airlift.mcp.operations.Operations.writeResult;
import static io.airlift.mcp.operations.RequestMetadata.SUPPORTED_VERSIONS;
import static jakarta.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public class OperationsImpl
        implements Operations
{
    private final McpMetadataMapper metadataMapper;
    private final JsonMapper jsonMapper;
    private final IconHelper iconHelper;
    private final Set<String> serverIcons;
    private final McpEntities entities;
    private final ValidationMode validationMode;
    private final PaginationUtil paginationUtil;
    private final Duration resourceSubscriptionCachePeriod;
    private final Duration streamingTimeout;

    @Inject
    OperationsImpl(
            McpMetadataMapper metadataMapper,
            JsonMapper jsonMapper,
            McpConfig mcpConfig,
            IconHelper iconHelper,
            @Named(MCP_SERVER_ICONS) Set<String> serverIcons,
            McpEntities entities,
            ValidationMode validationMode)
    {
        this.metadataMapper = requireNonNull(metadataMapper, "metadataMapper is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.iconHelper = requireNonNull(iconHelper, "iconHelper is null");
        this.serverIcons = requireNonNull(serverIcons, "serverIcons is null");
        this.entities = requireNonNull(entities, "entities is null");
        this.validationMode = requireNonNull(validationMode, "validationMode is null");

        paginationUtil = new PaginationUtil(mcpConfig);
        resourceSubscriptionCachePeriod = mcpConfig.getResourceSubscriptionCachePeriod().toJavaTime();
        streamingTimeout = mcpConfig.getEventStreamingTimeout().toJavaTime();
    }

    public record MetaOnly(Optional<Map<String, Object>> meta)
            implements Meta
    {
        public static final MetaOnly EMPTY_META = new MetaOnly(Optional.empty());

        public MetaOnly
        {
            meta = requireNonNullElse(meta, Optional.empty());
        }

        @Override
        public Object withMeta(Map<String, Object> meta)
        {
            return new MetaOnly(Optional.of(meta));
        }
    }

    @Override
    public void handleRpcRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest)
    {
        String method = rpcRequest.method();
        Object requestId = rpcRequest.id();

        updateRequestSpan(request, span -> span.setAttribute(MCP_METHOD_NAME, method));

        MessageWriter messageWriter = MessageWriter.newMessageWriter(response);
        request.setAttribute(MESSAGE_WRITER_ATTRIBUTE, messageWriter);

        Meta meta = rpcRequest.params().map(params -> jsonMapper.convertValue(params, MetaOnly.class))
                .orElse(MetaOnly.EMPTY_META);
        RequestMetadata requestMetadata = RequestMetadata.fromRequest(jsonMapper, request, meta, method, validationMode);
        RequestContextImpl requestContext = new RequestContextImpl(request, requestMetadata, jsonMapper, messageWriter, authenticated);

        McpMetadata metadata = metadataMapper.map(requestContext.request());

        Object result = switch (method) {
            case METHOD_TOOLS_LIST -> listTools(requestContext, metadata, convertParams(jsonMapper, rpcRequest, ListRequest.class));
            case METHOD_TOOLS_CALL -> callTool(requestContext, requestMetadata, convertParams(jsonMapper, rpcRequest, CallToolRequest.class));
            case METHOD_PROMPT_LIST -> listPrompts(requestContext, metadata, convertParams(jsonMapper, rpcRequest, ListRequest.class));
            case METHOD_PROMPT_GET -> getPrompt(requestContext, requestMetadata, convertParams(jsonMapper, rpcRequest, GetPromptRequest.class));
            case METHOD_RESOURCES_LIST -> listResources(requestContext, metadata, convertParams(jsonMapper, rpcRequest, ListRequest.class));
            case METHOD_RESOURCES_TEMPLATES_LIST -> listResourceTemplates(requestContext, metadata, convertParams(jsonMapper, rpcRequest, ListRequest.class));
            case METHOD_RESOURCES_READ -> readResources(requestContext, metadata, convertParams(jsonMapper, rpcRequest, ReadResourceRequest.class));
            case METHOD_COMPLETION_COMPLETE -> completionComplete(requestContext, convertParams(jsonMapper, rpcRequest, CompleteRequest.class));
            case METHOD_SERVER_DISCOVER -> serverDiscover(requestContext, metadata, requestMetadata);
            case METHOD_SUBSCRIPTIONS_LISTEN -> subscriptionsList(requestContext, requestId, convertParams(jsonMapper, rpcRequest, SubscriptionNotifications.class));
            default -> throw exception(METHOD_NOT_FOUND, "Unknown method: " + method);
        };

        writeResult(jsonMapper, messageWriter, response, requestId, result);
    }

    @Override
    public void handleRpcNotification(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest)
    {
        // not sure what we're supposed to do now
        response.setStatus(SC_ACCEPTED);
    }

    @Override
    public void handleRpcResponse(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcResponse<?> rpcResponse)
    {
        response.setStatus(SC_NOT_FOUND);
    }

    @Override
    public void handleRcpDeleteRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated)
    {
        response.setStatus(SC_METHOD_NOT_ALLOWED);
    }

    @Override
    public void handleRpcGetRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated)
    {
        response.setStatus(SC_METHOD_NOT_ALLOWED);
    }

    private Object subscriptionsList(RequestContextImpl requestContext, Object requestId, SubscriptionNotifications subscriptionNotifications)
    {
        SubscriptionLoop subscriptionLoop = new SubscriptionLoop(jsonMapper, requestId, entities, requestContext, subscriptionNotifications.notifications(), streamingTimeout, resourceSubscriptionCachePeriod);
        subscriptionLoop.run();

        return ImmutableMap.of();
    }

    public static ReadResourceResult readResources(McpEntities entities, McpRequestContext requestContext, ReadResourceRequest readResourceRequest)
    {
        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_RESOURCE_URI, readResourceRequest.uri()));

        List<ResourceContents> resourceContents = entities.readResourceContents(requestContext, readResourceRequest)
                .filter(contents -> !contents.isEmpty())
                .orElseThrow(() -> {
                    Map<String, String> data = ImmutableMap.of("uri", readResourceRequest.uri());
                    return new McpClientException(exception(INVALID_PARAMS, "Resource not found: " + readResourceRequest.uri(), data));
                });

        return new ReadResourceResult(resourceContents);
    }

    private void validateMcpName(RequestMetadata requestMetadata, String name)
    {
        if (validationMode == ValidationMode.STRICT) {
            String mcpName = requestMetadata.mcpName().orElse("");
            if (!mcpName.strip().equals(name)) {
                throw exception(HEADER_MISMATCH, "%s does not match request name: %s".formatted(HEADER_MCP_NAME, name));
            }
        }
    }

    private ListToolsResult listTools(RequestContextImpl requestContext, McpMetadata metadata, ListRequest listRequest)
    {
        List<Tool> localTools = entities.tools(requestContext);
        return paginationUtil.paginate(listRequest, localTools, Tool::name, (tools, newCursor) -> withCacheableResult(metadata, ListToolsResult.class, new ListToolsResult(tools, newCursor)));
    }

    private CallToolResult callTool(RequestContextImpl requestContext, RequestMetadata requestMetadata, CallToolRequest callToolRequest)
    {
        validateMcpName(requestMetadata, callToolRequest.name());
        entities.validateToolAllowed(requestContext, callToolRequest.name());

        ToolEntry toolEntry = entities.toolEntry(requestContext, callToolRequest.name())
                .orElseThrow(() -> exception(INVALID_PARAMS, "Tool not found: " + callToolRequest.name()));

        try {
            return toolEntry.toolHandler().callTool(requestContext, callToolRequest);
        }
        catch (McpClientException mcpClientException) {
            return CallToolResult.forError(mcpClientException);
        }
    }

    private ListPromptsResult listPrompts(RequestContextImpl requestContext, McpMetadata metadata, ListRequest listRequest)
    {
        List<Prompt> localPrompts = entities.prompts(requestContext);
        return paginationUtil.paginate(listRequest, localPrompts, Prompt::name, (prompts, nextCursor) -> withCacheableResult(metadata, ListPromptsResult.class, new ListPromptsResult(prompts, nextCursor)));
    }

    private GetPromptResult getPrompt(RequestContextImpl requestContext, RequestMetadata requestMetadata, GetPromptRequest getPromptRequest)
    {
        validateMcpName(requestMetadata, getPromptRequest.name());
        entities.validatePromptAllowed(requestContext, getPromptRequest.name());

        PromptEntry promptEntry = entities.promptEntry(requestContext, getPromptRequest.name())
                .orElseThrow(() -> exception(INVALID_PARAMS, "Prompt not found: " + getPromptRequest.name()));

        return promptEntry.promptHandler().getPrompt(requestContext, getPromptRequest);
    }

    private ListResourcesResult listResources(RequestContextImpl requestContext, McpMetadata metadata, ListRequest listRequest)
    {
        List<Resource> localResources = entities.resources(requestContext);
        return paginationUtil.paginate(listRequest, localResources, Resource::name, (resources, nextCursor) -> withCacheableResult(metadata, ListResourcesResult.class, new ListResourcesResult(resources, nextCursor)));
    }

    private ListResourceTemplatesResult listResourceTemplates(RequestContextImpl requestContext, McpMetadata metadata, ListRequest listRequest)
    {
        List<ResourceTemplate> localResourceTemplates = entities.resourceTemplates(requestContext);
        return paginationUtil.paginate(listRequest, localResourceTemplates, ResourceTemplate::name, ((resourceTemplates, nextCursor) -> withCacheableResult(metadata, ListResourceTemplatesResult.class, new ListResourceTemplatesResult(resourceTemplates, nextCursor))));
    }

    private ReadResourceResult readResources(RequestContextImpl requestContext, McpMetadata metadata, ReadResourceRequest readResourceRequest)
    {
        return withCacheableResult(metadata, ReadResourceResult.class, readResources(entities, requestContext, readResourceRequest));
    }

    private CompleteResult completionComplete(RequestContextImpl requestContext, CompleteRequest completeRequest)
    {
        return entities.completionEntry(requestContext, completeRequest.ref())
                .map(completionEntry -> completionEntry.handler().complete(requestContext, completeRequest))
                .orElseGet(CompleteResult::empty);
    }

    private DiscoverResult serverDiscover(RequestContextImpl requestContext, McpMetadata metadata, RequestMetadata requestMetadata)
    {
        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_PROTOCOL_VERSION, requestMetadata.protocol().value()));

        List<CompleteReference> completions = entities.completions(requestContext);
        List<Prompt> prompts = entities.prompts(requestContext);

        List<Resource> resources = entities.resources(requestContext);
        List<ResourceTemplate> resourceTemplates = entities.resourceTemplates(requestContext);
        List<Tool> tools = entities.tools(requestContext);

        ServerCapabilities serverCapabilities = new ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new CompletionCapabilities()),
                Optional.of(new LoggingCapabilities()),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(true)),
                resources.isEmpty() && resourceTemplates.isEmpty() ? Optional.empty() : Optional.of(new SubscribeListChanged(true, true)),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(true)),
                Optional.empty(),
                Optional.empty());

        Implementation serverImplementation = iconHelper.mapIcons(serverIcons).map(icons -> metadata.implementation().withAdditionalIcons(icons))
                .orElse(metadata.implementation());
        return new DiscoverResult(COMPLETE, SUPPORTED_VERSIONS, serverCapabilities, serverImplementation, metadata.instructions(), Optional.empty());
    }

    private <T extends CacheableResult> T withCacheableResult(McpMetadata metadata, Class<T> clazz, T result)
    {
        return clazz.cast(result.withCacheableResult(metadata.cacheableResultValues().ttlMs().orElse(0), metadata.cacheableResultValues().cacheScope().orElse(PRIVATE)));
    }
}
