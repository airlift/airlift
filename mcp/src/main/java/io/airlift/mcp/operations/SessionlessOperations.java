package io.airlift.mcp.operations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.airlift.log.Logger;
import io.airlift.mcp.McpEntities;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CompleteReference;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.InitializeResult;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListChanged;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.Prompt;
import io.airlift.mcp.model.Protocol;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.Resource;
import io.airlift.mcp.model.ResourceTemplate;
import io.airlift.mcp.model.SubscribeListChanged;
import io.airlift.mcp.model.Tool;
import io.airlift.mcp.reflection.IconHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.airlift.http.server.tracing.TracingServletFilter.updateRequestSpan;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.McpModule.MCP_SERVER_ICONS;
import static io.airlift.mcp.model.Constants.HEADER_SESSION_ID;
import static io.airlift.mcp.model.Constants.MESSAGE_WRITER_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_GET;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_CALL;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Constants.NOTIFICATION_CANCELLED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_INITIALIZED;
import static io.airlift.mcp.model.Constants.NOTIFICATION_ROOTS_LIST_CHANGED;
import static io.airlift.mcp.model.JsonRpcErrorCode.METHOD_NOT_FOUND;
import static io.airlift.mcp.model.Protocol.LATEST_PROTOCOL;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_METHOD_NAME;
import static io.opentelemetry.semconv.incubating.McpIncubatingAttributes.MCP_PROTOCOL_VERSION;
import static jakarta.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static java.util.Objects.requireNonNull;

public class SessionlessOperations
        implements Operations
{
    private static final Logger log = Logger.get(LegacyCancellationController.class);

    private final OperationsCommon operationsCommon;
    private final JsonMapper jsonMapper;
    private final McpMetadata metadata;
    private final McpEntities entities;
    private final Implementation serverImplementation;

    @Inject
    public SessionlessOperations(
            OperationsCommon operationsCommon,
            JsonMapper jsonMapper,
            IconHelper iconHelper,
            McpMetadata metadata,
            @Named(MCP_SERVER_ICONS) Set<String> serverIcons,
            McpEntities entities)
    {
        this.operationsCommon = requireNonNull(operationsCommon, "operationsCommon is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.entities = requireNonNull(entities, "entities is null");

        serverImplementation = iconHelper.mapIcons(serverIcons).map(icons -> metadata.implementation().withAdditionalIcons(icons))
                .orElse(metadata.implementation());
    }

    @Override
    public void handleRpcRequest(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest)
    {
        String method = rpcRequest.method();
        Object requestId = rpcRequest.id();

        updateRequestSpan(request, span -> span.setAttribute(MCP_METHOD_NAME, method));

        log.debug("Processing MCP request: %s, session: %s", method, request.getHeader(HEADER_SESSION_ID));

        MessageWriterImpl messageWriter = new MessageWriterImpl(response);
        request.setAttribute(MESSAGE_WRITER_ATTRIBUTE, messageWriter);

        RequestContextImpl requestContext = new RequestContextImpl(jsonMapper, Optional.empty(), request, response, messageWriter, authenticated);

        Object result = switch (method) {
            case METHOD_INITIALIZE -> handleInitialize(requestContext, operationsCommon.convertParams(rpcRequest, InitializeRequest.class));
            case METHOD_TOOLS_LIST -> operationsCommon.listTools(requestContext, operationsCommon.convertParams(rpcRequest, ListRequest.class));
            case METHOD_TOOLS_CALL -> operationsCommon.callTool(requestContext, operationsCommon.convertParams(rpcRequest, CallToolRequest.class));
            case METHOD_PROMPT_LIST -> operationsCommon.listPrompts(requestContext, operationsCommon.convertParams(rpcRequest, ListRequest.class));
            case METHOD_PROMPT_GET -> operationsCommon.getPrompt(requestContext, operationsCommon.convertParams(rpcRequest, GetPromptRequest.class));
            case METHOD_RESOURCES_LIST -> operationsCommon.listResources(requestContext, operationsCommon.convertParams(rpcRequest, ListRequest.class));
            case METHOD_RESOURCES_TEMPLATES_LIST -> operationsCommon.listResourceTemplates(requestContext, operationsCommon.convertParams(rpcRequest, ListRequest.class));
            case METHOD_RESOURCES_READ -> operationsCommon.readResources(requestContext, operationsCommon.convertParams(rpcRequest, ReadResourceRequest.class));
            case METHOD_COMPLETION_COMPLETE -> operationsCommon.completionComplete(requestContext, operationsCommon.convertParams(rpcRequest, CompleteRequest.class));
            case METHOD_PING -> ImmutableMap.of();
            default -> throw exception(METHOD_NOT_FOUND, "Unknown method: " + method);
        };

        response.setStatus(SC_OK);

        try {
            JsonRpcResponse<?> rpcResponse = new JsonRpcResponse<>(requestId, Optional.empty(), Optional.of(result));
            messageWriter.write(jsonMapper.writeValueAsString(rpcResponse));
            messageWriter.flushMessages();
        }
        catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private InitializeResult handleInitialize(RequestContextImpl requestContext, InitializeRequest initializeRequest)
    {
        Protocol protocol = Protocol.of(initializeRequest.protocolVersion())
                .orElse(LATEST_PROTOCOL);

        updateRequestSpan(requestContext.request(), span -> span.setAttribute(MCP_PROTOCOL_VERSION, protocol.value()));

        List<CompleteReference> completions = entities.completions(requestContext);
        List<Prompt> prompts = entities.prompts(requestContext);
        List<Resource> resources = entities.resources(requestContext);
        List<ResourceTemplate> resourceTemplates = entities.resourceTemplates(requestContext);
        List<Tool> tools = entities.tools(requestContext);

        InitializeResult.ServerCapabilities serverCapabilities = new InitializeResult.ServerCapabilities(
                completions.isEmpty() ? Optional.empty() : Optional.of(new InitializeResult.CompletionCapabilities()),
                Optional.empty(),
                prompts.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(false)),
                resources.isEmpty() && resourceTemplates.isEmpty() ? Optional.empty() : Optional.of(new SubscribeListChanged(false, false)),
                tools.isEmpty() ? Optional.empty() : Optional.of(new ListChanged(false)),
                Optional.empty());

        Implementation localImplementation = protocol.supportsIcons() ? serverImplementation : serverImplementation.simpleForm();

        return new InitializeResult(protocol.value(), serverCapabilities, localImplementation, metadata.instructions());
    }

    @Override
    public void handleRpcNotification(HttpServletRequest request, HttpServletResponse response, McpIdentity.Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest)
    {
        switch (rpcRequest.method()) {
            case NOTIFICATION_INITIALIZED -> {} // ignore
            case NOTIFICATION_CANCELLED -> {} // ignore
            case NOTIFICATION_ROOTS_LIST_CHANGED -> {} // ignore
            default -> log.warn("Unknown MCP notification method: %s", rpcRequest.method());
        }

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
}
