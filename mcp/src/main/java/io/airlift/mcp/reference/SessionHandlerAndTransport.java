package io.airlift.mcp.reference;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.session.McpSessionController;
import io.airlift.mcp.session.McpValueKey;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import io.modelcontextprotocol.spec.McpSchema.SetLevelRequest;
import io.modelcontextprotocol.spec.McpSchema.SubscribeRequest;
import io.modelcontextprotocol.spec.McpSchema.UnsubscribeRequest;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static io.airlift.mcp.model.LoggingLevel.INFO;
import static io.airlift.mcp.reference.ListVersion.NULL_UUID;
import static io.airlift.mcp.reference.Mapper.mapLoggingLevel;
import static io.airlift.mcp.reference.ReferenceFilter.HTTP_RESPONSE_ATTRIBUTE;
import static io.airlift.mcp.session.McpValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.session.McpValueKey.CLIENT_INFO;
import static io.airlift.mcp.session.McpValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.session.McpValueKey.PROMPTS_LIST_VERSION;
import static io.airlift.mcp.session.McpValueKey.RESOURCES_LIST_VERSION;
import static io.airlift.mcp.session.McpValueKey.RESOURCE_SUBSCRIPTION;
import static io.airlift.mcp.session.McpValueKey.RESOURCE_VERSION;
import static io.airlift.mcp.session.McpValueKey.ROOTS;
import static io.airlift.mcp.session.McpValueKey.TOOLS_LIST_VERSION;
import static io.airlift.mcp.session.McpValueKey.isSuffixedKey;
import static io.airlift.mcp.session.McpValueKey.keySuffix;
import static io.modelcontextprotocol.spec.HttpHeaders.MCP_SESSION_ID;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_INITIALIZE;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_LOGGING_SET_LEVEL;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_ROOTS_LIST_CHANGED;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_PING;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_RESOURCES_SUBSCRIBE;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_RESOURCES_UNSUBSCRIBE;
import static jakarta.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static java.util.Locale.ROOT;
import static java.util.Objects.requireNonNull;

public class SessionHandlerAndTransport
        implements McpStatelessServerHandler, McpStatelessServerTransport
{
    private static final Logger log = Logger.get(SessionHandlerAndTransport.class);

    private final McpSessionController sessionController;
    private final HttpServletStatelessServerTransport delegateTransport;
    private final ObjectMapper objectMapper;
    private final Duration eventLoopMaxDuration;
    private final Duration sessionPingInterval;
    private volatile McpStatelessServerHandler delegateHandler;

    public SessionHandlerAndTransport(
            McpSessionController sessionController,
            HttpServletStatelessServerTransport delegateTransport,
            ObjectMapper objectMapper,
            Duration eventLoopMaxDuration,
            Duration sessionPingInterval)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.delegateTransport = requireNonNull(delegateTransport, "delegateTransport is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.eventLoopMaxDuration = requireNonNull(eventLoopMaxDuration, "eventLoopMaxDuration is null");
        this.sessionPingInterval = requireNonNull(sessionPingInterval, "sessionPingInterval is null");
    }

    @Override
    public Mono<JSONRPCResponse> handleRequest(McpTransportContext transportContext, JSONRPCRequest rpcRequest)
    {
        requireNonNull(delegateHandler, "delegate is null");

        HttpServletRequest request = (HttpServletRequest) transportContext.get(McpMetadata.CONTEXT_REQUEST_KEY);

        return switch (rpcRequest.method()) {
            case METHOD_INITIALIZE -> handleInitialize(request, transportContext, rpcRequest);
            case METHOD_LOGGING_SET_LEVEL -> handleSetLoggingLevel(rpcRequest, requireSessionId(request));
            case METHOD_RESOURCES_SUBSCRIBE -> handleResourcesSubscribe(rpcRequest, requireSessionId(request));
            case METHOD_RESOURCES_UNSUBSCRIBE -> handleResourcesUnsubscribe(rpcRequest, requireSessionId(request));
            default -> delegateHandler.handleRequest(transportContext, rpcRequest);
        };
    }

    @Override
    public Mono<Void> handleNotification(McpTransportContext transportContext, McpSchema.JSONRPCNotification notification)
    {
        if (METHOD_NOTIFICATION_ROOTS_LIST_CHANGED.equals(notification.method())) {
            HttpServletRequest request = (HttpServletRequest) transportContext.get(McpMetadata.CONTEXT_REQUEST_KEY);
            sessionController.deleteValue(requireSessionId(request), ROOTS);
            return Mono.empty();
        }
        return delegateHandler.handleNotification(transportContext, notification);
    }

    void handleRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        try {
            switch (request.getMethod().toUpperCase(ROOT)) {
                case "GET" -> handleGetRequest(request, response);
                case "DELETE" -> handleDeleteRequest(request, response);
                default -> handleStandardRequest(request, response);
            }
        }
        catch (McpException mcpException) {
            if (response.isCommitted()) {
                throw mcpException;
            }

            log.error(mcpException, "MCP exception occurred while handling request. URI: %s, Session: %s", request.getRequestURI(), firstNonNull(request.getHeader(MCP_SESSION_ID), "n/a"));

            JsonRpcErrorDetail errorDetail = mcpException.errorDetail();
            response.setStatus((errorDetail.code() > 0) ? errorDetail.code() : SC_BAD_REQUEST);
            if ((errorDetail.message() != null) && !errorDetail.message().isBlank()) {
                response.getWriter().print(errorDetail.message());
            }
        }
    }

    private void handleStandardRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException
    {
        // Pre-read the request to see if it's a response
        BufferedReader reader = request.getReader();
        String content = reader.lines().collect(Collectors.joining("\n"));

        Map<String, Object> maybeMessage = objectMapper.readerFor(new TypeReference<>() {}).readValue(content);
        if (maybeMessage.containsKey("jsonrpc") && (maybeMessage.containsKey("result") || maybeMessage.containsKey("error"))) {
            JsonRpcResponse<?> rpcResponse = objectMapper.convertValue(maybeMessage, JsonRpcResponse.class);
            McpValueKey<JsonRpcMessage> messageKey = McpValueKey.RESPONSE.withSuffix(String.valueOf(rpcResponse.id()));
            String sessionId = requireSessionId(request);
            sessionController.upsertValue(sessionId, messageKey, rpcResponse);
            return;
        }

        // wrapped the pre-read content back so the reference transport can read it again
        BufferedReader wrappedReader = new BufferedReader(new StringReader(content));
        HttpServletRequestWrapper requestWrapper = new HttpServletRequestWrapper(request)
        {
            @Override
            public BufferedReader getReader()
            {
                return wrappedReader;
            }
        };

        delegateTransport.service(requestWrapper, response);
    }

    private void handleDeleteRequest(HttpServletRequest request, HttpServletResponse response)
    {
        String sessionId = request.getHeader(MCP_SESSION_ID);
        if (sessionId != null) {
            sessionController.deleteSession(sessionId);
        }
        response.setStatus(SC_ACCEPTED);
    }

    private void handleGetRequest(HttpServletRequest request, HttpServletResponse response)
    {
        String sessionId = request.getHeader(MCP_SESSION_ID);
        if (sessionId == null) {
            throw exception(SC_BAD_REQUEST, "Missing MCP session ID");
        }
        if (!sessionController.isValidSession(sessionId)) {
            throw exception(SC_NOT_FOUND, "Invalid MCP session ID");
        }

        log.debug("Handling GET request for session %s", sessionId);

        List<ListVersion> listVersions = new ArrayList<>();
        listVersions.add(new ListVersion(sessionController, sessionId, TOOLS_LIST_VERSION, METHOD_NOTIFICATION_TOOLS_LIST_CHANGED));
        listVersions.add(new ListVersion(sessionController, sessionId, PROMPTS_LIST_VERSION, METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED));
        listVersions.add(new ListVersion(sessionController, sessionId, RESOURCES_LIST_VERSION, METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED));

        response.setStatus(SC_OK);
        try {
            PrintWriter writer = response.getWriter();
            if (!(writer instanceof SsePrintWriter ssePrintWriter)) {
                // TODO
                throw new IOException("Invalid response writer");
            }

            Duration remaining = Duration.from(eventLoopMaxDuration);
            while (remaining.isPositive()) {
                Stopwatch stopwatch = Stopwatch.createStarted();

                Optional<List<JsonRpcRequest<?>>> updates = sessionController.waitValueCondition(sessionId, () -> updateListsAndSubscriptions(sessionId, listVersions), sessionPingInterval);

                if (updates.isPresent()) {
                    for (JsonRpcRequest<?> update : updates.get()) {
                        ssePrintWriter.writeMessage(objectMapper.writeValueAsString(update));
                    }
                }
                else {
                    ssePrintWriter.writeMessage(objectMapper.writeValueAsString(buildNotification(METHOD_PING)));
                }

                ssePrintWriter.flush();

                remaining = remaining.minus(stopwatch.elapsed());
            }

            log.debug("Finished handling GET request for session %s", sessionId);
        }
        catch (Exception e) {
            // TODO
            throw new RuntimeException(e);
        }
    }

    private Optional<List<JsonRpcRequest<?>>> updateListsAndSubscriptions(String sessionId, List<ListVersion> listVersions)
    {
        List<JsonRpcRequest<?>> updates = new ArrayList<>();

        listVersions.stream()
                .filter(ListVersion::wasUpdated)
                .forEach(changedList -> updates.add(buildNotification(changedList.notification())));

        sessionController.currentValueKeys(sessionId)
                .stream()
                .filter(key -> isSuffixedKey(RESOURCE_SUBSCRIPTION, key))
                .forEach(key -> {
                    String uri = keySuffix(RESOURCE_SUBSCRIPTION, key);

                    var resourceSubscriptionKey = RESOURCE_SUBSCRIPTION.withSuffix(uri);
                    var resourceVersionKey = RESOURCE_VERSION.withSuffix(uri);

                    UUID subscribedVersion = sessionController.currentValue(sessionId, resourceSubscriptionKey, NULL_UUID);
                    UUID currentVersion = sessionController.currentValue(sessionId, resourceVersionKey, NULL_UUID);

                    if (!subscribedVersion.equals(currentVersion)) {
                        sessionController.upsertValue(sessionId, resourceSubscriptionKey, currentVersion);
                        updates.add(buildNotification(McpSchema.METHOD_NOTIFICATION_RESOURCES_UPDATED, ImmutableMap.of("uri", uri)));
                    }
                });

        return updates.isEmpty() ? Optional.empty() : Optional.of(updates);
    }

    @Override
    public void setMcpHandler(McpStatelessServerHandler mcpHandler)
    {
        delegateHandler = requireNonNull(mcpHandler, "mcpHandler is null");
        delegateTransport.setMcpHandler(this);
    }

    @Override
    public Mono<Void> closeGracefully()
    {
        return delegateTransport.closeGracefully();
    }

    @Override
    public void close()
    {
        delegateTransport.close();
    }

    @Override
    public List<String> protocolVersions()
    {
        return delegateTransport.protocolVersions();
    }

    static String requireSessionId(HttpServletRequest request)
    {
        String sessionId = request.getHeader(MCP_SESSION_ID);
        if (sessionId == null) {
            throw exception("Missing MCP session ID");
        }
        return sessionId;
    }

    private Mono<JSONRPCResponse> handleResourcesUnsubscribe(JSONRPCRequest rpcRequest, String sessionId)
    {
        UnsubscribeRequest unsubscribeRequest = objectMapper.convertValue(rpcRequest.params(), UnsubscribeRequest.class);
        sessionController.deleteValue(sessionId, RESOURCE_SUBSCRIPTION.withSuffix(unsubscribeRequest.uri()));
        return Mono.just(new JSONRPCResponse(McpSchema.JSONRPC_VERSION, rpcRequest.id(), ImmutableMap.of(), null));
    }

    private Mono<JSONRPCResponse> handleResourcesSubscribe(JSONRPCRequest rpcRequest, String sessionId)
    {
        SubscribeRequest subscribeRequest = objectMapper.convertValue(rpcRequest.params(), SubscribeRequest.class);
        UUID currentVersion = sessionController.currentValue(sessionId, RESOURCE_VERSION.withSuffix(subscribeRequest.uri()), NULL_UUID);
        sessionController.upsertValue(sessionId, RESOURCE_SUBSCRIPTION.withSuffix(subscribeRequest.uri()), currentVersion);
        return Mono.just(new JSONRPCResponse(McpSchema.JSONRPC_VERSION, rpcRequest.id(), ImmutableMap.of(), null));
    }

    private Mono<JSONRPCResponse> handleInitialize(HttpServletRequest request, McpTransportContext transportContext, JSONRPCRequest rpcRequest)
    {
        InitializeRequest initializeRequest = objectMapper.convertValue(rpcRequest.params(), InitializeRequest.class);

        HttpServletResponse response = (HttpServletResponse) request.getAttribute(HTTP_RESPONSE_ATTRIBUTE);
        String sessionId = sessionController.newSession();

        sessionController.upsertValue(sessionId, CLIENT_CAPABILITIES, initializeRequest.capabilities());
        sessionController.upsertValue(sessionId, CLIENT_INFO, initializeRequest.clientInfo());
        sessionController.upsertValue(sessionId, LOGGING_LEVEL, INFO);

        response.setHeader(MCP_SESSION_ID, sessionId);

        return delegateHandler.handleRequest(transportContext, rpcRequest);
    }

    private Mono<JSONRPCResponse> handleSetLoggingLevel(JSONRPCRequest rpcRequest, String sessionId)
    {
        SetLevelRequest setLevelRequest = objectMapper.convertValue(rpcRequest.params(), SetLevelRequest.class);
        sessionController.upsertValue(sessionId, LOGGING_LEVEL, mapLoggingLevel(setLevelRequest.level()));
        return Mono.just(new JSONRPCResponse(McpSchema.JSONRPC_VERSION, rpcRequest.id(), ImmutableMap.of(), null));
    }
}
