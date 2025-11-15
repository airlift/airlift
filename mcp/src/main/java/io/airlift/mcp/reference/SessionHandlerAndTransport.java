package io.airlift.mcp.reference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import io.airlift.log.Logger;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.model.Event;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.session.McpSessionController;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.net.HttpHeaders.LAST_EVENT_ID;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static io.airlift.mcp.reference.Mapper.mapLoggingLevel;
import static io.airlift.mcp.reference.ReferenceFilter.HTTP_RESPONSE_ATTRIBUTE;
import static io.airlift.mcp.reference.ReferenceServer.LIST_ROOTS_REQUEST_ID;
import static io.airlift.mcp.session.McpValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.session.McpValueKey.CLIENT_INFO;
import static io.airlift.mcp.session.McpValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.session.McpValueKey.resourceSubscription;
import static io.modelcontextprotocol.spec.HttpHeaders.MCP_SESSION_ID;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_INITIALIZE;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_LOGGING_SET_LEVEL;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_ROOTS_LIST_CHANGED;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_PING;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_RESOURCES_SUBSCRIBE;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_RESOURCES_UNSUBSCRIBE;
import static io.modelcontextprotocol.spec.McpSchema.deserializeJsonRpcMessage;
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
    private final McpJsonMapper mcpJsonMapper;
    private final ResponseListenerController responseListenerController;
    private final Duration eventLoopMaxDuration;
    private final Duration sessionPingInterval;
    private volatile McpStatelessServerHandler delegateHandler;

    public SessionHandlerAndTransport(
            McpSessionController sessionController,
            HttpServletStatelessServerTransport delegateTransport,
            ObjectMapper objectMapper,
            McpJsonMapper mcpJsonMapper,
            ResponseListenerController responseListenerController,
            Duration eventLoopMaxDuration,
            Duration sessionPingInterval)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.delegateTransport = requireNonNull(delegateTransport, "delegateTransport is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.mcpJsonMapper = requireNonNull(mcpJsonMapper, "mcpJsonMapper is null");
        this.responseListenerController = responseListenerController;
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
            try {
                sendListRootsRequest(requireSessionId(request));
            }
            catch (JsonProcessingException e) {
                throw exception(e);
            }
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

        JSONRPCMessage message = deserializeJsonRpcMessage(mcpJsonMapper, content);
        if (message instanceof JSONRPCResponse) {
            String sessionId = requireSessionId(request);
            sessionController.addEvent(sessionId, content);
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

        response.setStatus(SC_OK);
        try {
            PrintWriter writer = response.getWriter();
            if (!(writer instanceof SsePrintWriter ssePrintWriter)) {
                // TODO
                throw new IOException("Invalid response writer");
            }

            sendListRootsRequest(sessionId);

            JsonRpcRequest<Object> listRootsRequest = JsonRpcRequest.buildRequest(UUID.randomUUID().toString(), McpSchema.METHOD_ROOTS_LIST);
            sessionController.addEvent(sessionId, objectMapper.writeValueAsString(listRootsRequest));

            // returns -1 if the header isn't present
            long lastEventId = request.getIntHeader(LAST_EVENT_ID);

            Stopwatch stopwatch = Stopwatch.createStarted();

            while (stopwatch.elapsed().compareTo(eventLoopMaxDuration) < 0) {
                if (!sessionController.isValidSession(sessionId)) {
                    log.debug("Session %s is no longer valid, closing GET request", sessionId);
                    break;
                }

                List<Event> events = sessionController.pollEvents(sessionId, lastEventId, sessionPingInterval);
                if (events.isEmpty()) {
                    ssePrintWriter.writeMessage(objectMapper.writeValueAsString(buildNotification(METHOD_PING)));
                }
                else {
                    for (Event event : events) {
                        handleEvent(ssePrintWriter, event, sessionId);
                        lastEventId = event.id();
                    }
                }
                ssePrintWriter.flush();
            }

            log.debug("Finished handling GET request for session %s", sessionId);
        }
        catch (IOException e) {
            // TODO
            throw new RuntimeException(e);
        }
    }

    private void sendListRootsRequest(String sessionId)
            throws JsonProcessingException
    {
        if (sessionController.currentValue(sessionId, CLIENT_CAPABILITIES).map(clientCapabilities -> clientCapabilities.roots().isPresent()).isPresent()) {
            JsonRpcRequest<Object> listRootsRequest = JsonRpcRequest.buildRequest(LIST_ROOTS_REQUEST_ID, McpSchema.METHOD_ROOTS_LIST);
            sessionController.addEvent(sessionId, objectMapper.writeValueAsString(listRootsRequest));
        }
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

    private void handleEvent(SsePrintWriter ssePrintWriter, Event event, String sessionId)
    {
        try {
            Map<String, Object> tree = objectMapper.readerFor(new TypeReference<>() {}).readValue(event.data());
            if (JsonRpcRequest.JSON_RPC_VERSION.equals(tree.get("jsonrpc")) && (tree.containsKey("error") || tree.containsKey("result"))) {
                JsonRpcResponse<?> rpcResponse = objectMapper.convertValue(tree, JsonRpcResponse.class);
                responseListenerController.notifyListeners(sessionId, rpcResponse);
                return;
            }

            ssePrintWriter.writeMessage(event.data(), Optional.of(Long.toString(event.id())));
        }
        catch (JsonProcessingException e) {
            throw exception(e);
        }
    }

    private Mono<JSONRPCResponse> handleResourcesUnsubscribe(JSONRPCRequest rpcRequest, String sessionId)
    {
        UnsubscribeRequest unsubscribeRequest = objectMapper.convertValue(rpcRequest.params(), UnsubscribeRequest.class);
        sessionController.deleteValue(sessionId, resourceSubscription(unsubscribeRequest.uri()));
        return Mono.just(new JSONRPCResponse(McpSchema.JSONRPC_VERSION, rpcRequest.id(), ImmutableMap.of(), null));
    }

    private Mono<JSONRPCResponse> handleResourcesSubscribe(JSONRPCRequest rpcRequest, String sessionId)
    {
        SubscribeRequest subscribeRequest = objectMapper.convertValue(rpcRequest.params(), SubscribeRequest.class);
        sessionController.upsertValue(sessionId, resourceSubscription(subscribeRequest.uri()), true);
        return Mono.just(new JSONRPCResponse(McpSchema.JSONRPC_VERSION, rpcRequest.id(), ImmutableMap.of(), null));
    }

    private Mono<JSONRPCResponse> handleInitialize(HttpServletRequest request, McpTransportContext transportContext, JSONRPCRequest rpcRequest)
    {
        InitializeRequest initializeRequest = objectMapper.convertValue(rpcRequest.params(), InitializeRequest.class);

        HttpServletResponse response = (HttpServletResponse) request.getAttribute(HTTP_RESPONSE_ATTRIBUTE);
        String sessionId = sessionController.newSession();

        sessionController.upsertValue(sessionId, CLIENT_CAPABILITIES, initializeRequest.capabilities());
        sessionController.upsertValue(sessionId, CLIENT_INFO, initializeRequest.clientInfo());

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
