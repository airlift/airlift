
package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.ErrorHandler;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpIdentityMapper;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.features.ParsedRpcMessage;
import io.airlift.mcp.features.RpcMessageParser;
import io.airlift.mcp.legacy.sessions.LegacySession;
import io.airlift.mcp.legacy.sessions.LegacySessionId;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.opentelemetry.api.common.AttributeKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static com.google.common.net.HttpHeaders.WWW_AUTHENTICATE;
import static io.airlift.http.server.tracing.TracingServletFilter.updateRequestSpan;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.legacy.sessions.LegacySessionController.optionalSessionId;
import static io.airlift.mcp.model.Constants.HEADER_SESSION_ID;
import static io.airlift.mcp.model.Constants.MCP_IDENTITY_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.MESSAGE_WRITER_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.RPC_MESSAGE_ATTRIBUTE;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

public class InternalFilter
        extends HttpFilter
{
    private static final Logger log = Logger.get(InternalFilter.class);

    private static final Set<String> ALLOWED_HTTP_METHODS = ImmutableSet.of("GET", "POST", "DELETE");

    // TODO - remove when the next rev of opentelemetry-semconv is released
    // see: https://github.com/open-telemetry/semantic-conventions-java/pull/396/changes#diff-1c3840320c0c5bfe484b22e6db177a87b014591107c61f9d5763f92117ddf7ff
    public static final AttributeKey<String> MCP_RESOURCE_URI = stringKey("mcp.resource.uri");
    static final AttributeKey<String> MCP_METHOD_NAME = stringKey("mcp.method.name");
    static final AttributeKey<String> MCP_SESSION_ID = stringKey("mcp.session.id");

    private final McpMetadata metadata;
    private final McpIdentityMapper identityMapper;
    private final JsonMapper jsonMapper;
    private final ErrorHandler errorHandler;
    private final RpcMessageParser rpcMessageParser;
    private final boolean httpGetEventsEnabled;

    @Inject
    public InternalFilter(
            McpMetadata metadata,
            McpIdentityMapper identityMapper,
            JsonMapper jsonMapper,
            ErrorHandler errorHandler,
            RpcMessageParser rpcMessageParser,
            McpConfig mcpConfig)
    {
        this.metadata = requireNonNull(metadata, "metadata is null");
        this.identityMapper = requireNonNull(identityMapper, "identityMapper is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.errorHandler = requireNonNull(errorHandler, "errorHandler is null");
        this.rpcMessageParser = requireNonNull(rpcMessageParser, "rpcRequestParser is null");

        httpGetEventsEnabled = mcpConfig.isHttpGetEventsEnabled();
    }

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        if (!isMcpRequest(request)) {
            chain.doFilter(request, response);
            return;
        }

        optionalSessionId(request).ifPresent(sessionId ->
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

        ParsedRpcMessage parsedRpcMessage = rpcMessageParser.parse(request);

        switch (request.getMethod().toUpperCase(ROOT)) {
            case POST -> handleMcpPostRequest(request, response, parsedRpcMessage, authenticated);
            case GET -> handleMpcGetRequest(request, response, parsedRpcMessage, authenticated);
            case DELETE -> handleMcpDeleteRequest(request, response, parsedRpcMessage, authenticated);
            default -> response.setStatus(SC_NOT_FOUND);
        }
    }

    private void handleMpcGetRequest(HttpServletRequest request, HttpServletResponse response, ParsedRpcMessage parsedRpcMessage, Authenticated<?> authenticated)
    {
        if (httpGetEventsEnabled) {
            callParsedFeature(request, response, authenticated, parsedRpcMessage);
        }
        else {
            response.setStatus(SC_METHOD_NOT_ALLOWED);
        }
    }

    private void handleMcpDeleteRequest(HttpServletRequest request, HttpServletResponse response, ParsedRpcMessage parsedRpcMessage, Authenticated<?> authenticated)
    {
        callParsedFeature(request, response, authenticated, parsedRpcMessage);

        response.setStatus(SC_ACCEPTED);
    }

    private void handleMcpPostRequest(HttpServletRequest request, HttpServletResponse response, ParsedRpcMessage parsedRpcMessage, Authenticated<?> authenticated)
            throws Exception
    {
        InternalMessageWriter messageWriter = buildMessageWriter(request, response);

        String accept = request.getHeader(ACCEPT);
        if (accept == null || !(accept.contains(APPLICATION_JSON) && accept.contains(SERVER_SENT_EVENTS))) {
            throw exception(INVALID_REQUEST, "Both application/json and text/event-stream required in Accept header");
        }

        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding(UTF_8.name());

        switch (parsedRpcMessage.message()) {
            case JsonRpcRequest<?> rpcRequest -> handleRpcRequest(request, response, authenticated, parsedRpcMessage, rpcRequest, messageWriter);
            case JsonRpcResponse<?> rpcResponse -> handleRpcResponse(request, response, authenticated, parsedRpcMessage, rpcResponse);
            default -> throw exception(SC_BAD_REQUEST, "The server accepts either requests or notifications");
        }
    }

    private static InternalMessageWriter buildMessageWriter(HttpServletRequest request, HttpServletResponse response)
    {
        InternalMessageWriter messageWriter = new InternalMessageWriter(response);
        request.setAttribute(MESSAGE_WRITER_ATTRIBUTE, messageWriter);
        return messageWriter;
    }

    private void handleRpcResponse(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, ParsedRpcMessage parsedRpcMessage, JsonRpcResponse<?> rpcResponse)
    {
        log.debug("Processing MCP response: %s, session: %s", rpcResponse.id(), optionalSessionId(request).map(LegacySessionId::id).orElse("-"));

        callParsedFeature(request, response, authenticated, parsedRpcMessage);

        response.setStatus(SC_ACCEPTED);
    }

    private void callParsedFeature(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, ParsedRpcMessage parsedRpcMessage)
    {
        InternalMessageWriter messageWriter = buildMessageWriter(request, response);
        Optional<LegacySession> legacySession = rpcMessageParser.parseLegacySession(request);
        InternalRequestContext requestContext = new InternalRequestContext(jsonMapper, legacySession, request, response, messageWriter, authenticated);

        parsedRpcMessage.feature().apply(requestContext);

        parsedRpcMessage.features().checkSaveSentMessages(requestContext);
    }

    private void handleRpcRequest(HttpServletRequest request, HttpServletResponse response, Authenticated<?> authenticated, ParsedRpcMessage parsedRpcMessage, JsonRpcRequest<?> rpcRequest, InternalMessageWriter messageWriter)
            throws Exception
    {
        request.setAttribute(RPC_MESSAGE_ATTRIBUTE, parsedRpcMessage.message());

        Object requestId = parsedRpcMessage.message().id();

        updateRequestSpan(request, span -> span.setAttribute(MCP_METHOD_NAME, rpcRequest.method()));

        log.debug("Processing MCP request: %s, session: %s", rpcRequest.method(), request.getHeader(HEADER_SESSION_ID));

        Optional<LegacySession> legacySession = rpcMessageParser.parseLegacySession(request);
        InternalRequestContext requestContext = new InternalRequestContext(jsonMapper, legacySession, request, response, messageWriter, authenticated);

        Object result = parsedRpcMessage.feature().apply(requestContext);
        if (rpcRequest.isNotification()) {
            response.setStatus(SC_ACCEPTED);
        }
        else {
            response.setStatus(SC_OK);

            JsonRpcResponse<?> rpcResponse = new JsonRpcResponse<>(requestId, Optional.empty(), Optional.of(result));
            messageWriter.write(jsonMapper.writeValueAsString(rpcResponse));
        }
        messageWriter.flushMessages();

        parsedRpcMessage.features().checkSaveSentMessages(requestContext);
    }

    private boolean isMcpRequest(HttpServletRequest request)
    {
        if (ALLOWED_HTTP_METHODS.contains(request.getMethod().toUpperCase(ROOT))) {
            return metadata.uriPath().equals(request.getRequestURI());
        }
        return false;
    }
}
