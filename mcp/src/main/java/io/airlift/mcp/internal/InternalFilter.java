package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.ErrorHandler;
import io.airlift.mcp.McpIdentityMapper;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CompleteRequest;
import io.airlift.mcp.model.GetPromptRequest;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListRequest;
import io.airlift.mcp.model.McpIdentity;
import io.airlift.mcp.model.McpIdentity.Authenticated;
import io.airlift.mcp.model.ReadResourceRequest;
import io.airlift.mcp.model.SetLevelRequest;
import io.airlift.mcp.sessions.SessionController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.internal.InternalRequestContext.requireSessionId;
import static io.airlift.mcp.model.Constants.HEADER_SESSION_ID;
import static io.airlift.mcp.model.Constants.MCP_IDENTITY_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.MESSAGE_WRITER_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.METHOD_COMPLETION_COMPLETE;
import static io.airlift.mcp.model.Constants.METHOD_INITIALIZE;
import static io.airlift.mcp.model.Constants.METHOD_LOGGING_SET_LEVEL;
import static io.airlift.mcp.model.Constants.METHOD_PING;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_GET;
import static io.airlift.mcp.model.Constants.METHOD_PROMPT_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_READ;
import static io.airlift.mcp.model.Constants.METHOD_RESOURCES_TEMPLATES_LIST;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_CALL;
import static io.airlift.mcp.model.Constants.METHOD_TOOLS_LIST;
import static io.airlift.mcp.model.Constants.RPC_MESSAGE_ATTRIBUTE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.METHOD_NOT_FOUND;
import static io.airlift.mcp.model.JsonRpcErrorCode.PARSE_ERROR;
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

    private final McpMetadata metadata;
    private final McpIdentityMapper identityMapper;
    private final InternalMcpServer mcpServer;
    private final ObjectMapper objectMapper;
    private final ErrorHandler errorHandler;
    private final Optional<SessionController> sessionController;

    @Inject
    public InternalFilter(McpMetadata metadata, McpIdentityMapper identityMapper, InternalMcpServer mcpServer, ObjectMapper objectMapper, ErrorHandler errorHandler, Optional<SessionController> sessionController)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.identityMapper = requireNonNull(identityMapper, "identityMapper is null");
        this.mcpServer = requireNonNull(mcpServer, "mcpServer is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.errorHandler = requireNonNull(errorHandler, "errorHandler is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        if (!isMcpRequest(request)) {
            chain.doFilter(request, response);
            return;
        }
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
            case GET -> response.setStatus(SC_METHOD_NOT_ALLOWED);
            case DELETE -> handleMcpDeleteRequest(request, response);
            default -> response.setStatus(SC_NOT_FOUND);
        }
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
                case JsonRpcResponse<?> rpcResponse -> handleRpcResponse(request, response, rpcResponse);
                default -> throw exception(SC_BAD_REQUEST, "The server accepts either requests or notifications");
            }
        }
    }

    private void handleRpcResponse(HttpServletRequest request, HttpServletResponse response, JsonRpcResponse<?> rpcResponse)
    {
        log.debug("Processing MCP response: %s, session: %s", rpcResponse.id(), request.getHeader(HEADER_SESSION_ID));

        response.setStatus(SC_ACCEPTED);
    }

    private void handleRpcNotification(HttpServletRequest request, HttpServletResponse response, JsonRpcRequest<?> rpcRequest)
    {
        log.debug("Processing MCP notification: %s, session: %s", rpcRequest.method(), request.getHeader(HEADER_SESSION_ID));

        response.setStatus(SC_ACCEPTED);
    }

    private void handleRpcRequest(HttpServletRequest request, HttpServletResponse
            response, Authenticated<?> authenticated, JsonRpcRequest<?> rpcRequest, InternalMessageWriter messageWriter)
            throws Exception
    {
        request.setAttribute(RPC_MESSAGE_ATTRIBUTE, rpcRequest);

        log.debug("Processing MCP request: %s, session: %s", rpcRequest.method(), request.getHeader(HEADER_SESSION_ID));

        validateSession(request, rpcRequest);

        Object result = switch (rpcRequest.method()) {
            case METHOD_INITIALIZE -> mcpServer.initialize(response, authenticated, convertParams(rpcRequest, InitializeRequest.class));
            case METHOD_TOOLS_LIST -> mcpServer.listTools(convertParams(rpcRequest, ListRequest.class));
            case METHOD_TOOLS_CALL -> mcpServer.callTool(request, messageWriter, convertParams(rpcRequest, CallToolRequest.class));
            case METHOD_PROMPT_LIST -> mcpServer.listPrompts(convertParams(rpcRequest, ListRequest.class));
            case METHOD_PROMPT_GET -> mcpServer.getPrompt(request, messageWriter, convertParams(rpcRequest, GetPromptRequest.class));
            case METHOD_RESOURCES_LIST -> mcpServer.listResources(convertParams(rpcRequest, ListRequest.class));
            case METHOD_RESOURCES_TEMPLATES_LIST -> mcpServer.listResourceTemplates(convertParams(rpcRequest, ListRequest.class));
            case METHOD_RESOURCES_READ -> mcpServer.readResources(request, messageWriter, convertParams(rpcRequest, ReadResourceRequest.class));
            case METHOD_PING -> ImmutableMap.of();
            case METHOD_COMPLETION_COMPLETE -> mcpServer.completionComplete(request, messageWriter, convertParams(rpcRequest, CompleteRequest.class));
            case METHOD_LOGGING_SET_LEVEL -> mcpServer.setLoggingLevel(request, convertParams(rpcRequest, SetLevelRequest.class));
            default -> throw exception(METHOD_NOT_FOUND, "Unknown method: " + rpcRequest.method());
        };

        response.setStatus(SC_OK);

        JsonRpcResponse<?> rpcResponse = new JsonRpcResponse<>(rpcRequest.id(), Optional.empty(), Optional.of(result));
        messageWriter.write(objectMapper.writeValueAsString(rpcResponse));
        messageWriter.flushMessages();
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

        throw exception(PARSE_ERROR, "Cannot deserialize JSONRPCMessage: " + json);
    }
}
