package io.airlift.mcp.internal;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.mcp.ErrorHandler;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpIdentityMapper;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.operations.Operations;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Set;

import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.MCP_IDENTITY_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.RPC_MESSAGE_ATTRIBUTE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.model.JsonRpcErrorCode.PARSE_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

public class InternalFilter
        extends HttpFilter
{
    private static final Set<String> ALLOWED_HTTP_METHODS = ImmutableSet.of("GET", "POST", "DELETE");

    private final McpMetadata metadata;
    private final McpIdentityMapper identityMapper;
    private final JsonMapper jsonMapper;
    private final ErrorHandler errorHandler;
    private final Operations operations;

    @Inject
    public InternalFilter(
            McpMetadata metadata,
            McpIdentityMapper identityMapper,
            JsonMapper jsonMapper,
            ErrorHandler errorHandler,
            Operations operations)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.identityMapper = requireNonNull(identityMapper, "identityMapper is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.errorHandler = requireNonNull(errorHandler, "errorHandler is null");
        this.operations = requireNonNull(operations, "operations is null");
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
            case GET -> operations.handleRpcGetRequest(request, response, authenticated);
            case DELETE -> operations.handleRcpDeleteRequest(request, response, authenticated);
            default -> response.setStatus(SC_NOT_FOUND);
        }
    }

    private void handleMcpPostRequest(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated)
            throws Exception
    {
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
                case JsonRpcRequest<?> rpcRequest when (rpcRequest.id() != null) -> {
                    request.setAttribute(RPC_MESSAGE_ATTRIBUTE, rpcRequest);
                    operations.handleRpcRequest(request, response, authenticated, rpcRequest);
                }

                case JsonRpcRequest<?> rpcRequest -> {
                    request.setAttribute(RPC_MESSAGE_ATTRIBUTE, rpcRequest);
                    operations.handleRpcNotification(request, response, authenticated, rpcRequest);
                }

                case JsonRpcResponse<?> rpcResponse -> {
                    request.setAttribute(RPC_MESSAGE_ATTRIBUTE, rpcResponse);
                    operations.handleRpcResponse(request, response, authenticated, rpcResponse);
                }

                default -> throw exception(SC_BAD_REQUEST, "The server accepts either requests or notifications");
            }
        }
    }

    private boolean isMcpRequest(HttpServletRequest request)
    {
        if (ALLOWED_HTTP_METHODS.contains(request.getMethod().toUpperCase(ROOT))) {
            return metadata.uriPath().equals(request.getRequestURI());
        }
        return false;
    }

    private Object deserializeJsonRpcMessage(String json)
    {
        JsonNode tree = jsonMapper.readTree(json);

        if (tree.has("method")) {
            return jsonMapper.treeToValue(tree, JsonRpcRequest.class);
        }

        if (tree.has("result") || tree.has("error")) {
            return jsonMapper.treeToValue(tree, JsonRpcResponse.class);
        }

        throw exception(PARSE_ERROR, "Cannot deserialize JsonRpcMessage: " + json);
    }
}
