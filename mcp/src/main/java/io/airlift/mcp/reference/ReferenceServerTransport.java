package io.airlift.mcp.reference;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpMetadata;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.WebApplicationException;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.reference.ReferenceFilter.HTTP_RESPONSE_ATTRIBUTE;
import static io.airlift.mcp.sessions.SessionKey.LOGGING_LEVEL;
import static io.modelcontextprotocol.common.McpTransportContext.KEY;
import static io.modelcontextprotocol.spec.HttpHeaders.MCP_SESSION_ID;
import static io.modelcontextprotocol.spec.McpSchema.ErrorCodes.INTERNAL_ERROR;
import static io.modelcontextprotocol.spec.McpSchema.ErrorCodes.INVALID_REQUEST;
import static io.modelcontextprotocol.spec.McpSchema.JSONRPC_VERSION;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_INITIALIZE;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_LOGGING_SET_LEVEL;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_INITIALIZED;
import static io.modelcontextprotocol.spec.McpSchema.deserializeJsonRpcMessage;
import static jakarta.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static reactor.core.publisher.Mono.just;

// based on io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport.java
public class ReferenceServerTransport
        extends HttpServlet
        implements McpStatelessServerTransport
{
    public static final String CONTEXT_MESSAGE_WRITER_KEY = McpMetadata.class.getName() + ".messageWriter";

    private static final Logger logger = Logger.get(ReferenceServerTransport.class);

    private final McpJsonMapper mcpJsonMapper;
    private final Optional<SessionController> sessionController;
    private volatile McpStatelessServerHandler mcpHandler;
    private volatile boolean isClosing;

    @Inject
    public ReferenceServerTransport(McpJsonMapper mcpJsonMapper, Optional<SessionController> sessionController)
    {
        this.mcpJsonMapper = requireNonNull(mcpJsonMapper, "jsonMapper is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
    }

    @Override
    public void setMcpHandler(McpStatelessServerHandler mcpHandler)
    {
        this.mcpHandler = requireNonNull(mcpHandler, "mcpHandler is null");
    }

    @Override
    public Mono<Void> closeGracefully()
    {
        return Mono.fromRunnable(() -> isClosing = true);
    }

    @Override
    public void destroy()
    {
        closeGracefully().block();
        super.destroy();
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
    {
        sessionController.ifPresentOrElse(controller -> {
            SessionId sessionId = requireSessionId(request);
            controller.deleteSession(sessionId);
            response.setStatus(SC_ACCEPTED);
        }, () -> {
            throw new WebApplicationException(SC_METHOD_NOT_ALLOWED);
        });
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        response.sendError(SC_METHOD_NOT_ALLOWED);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        if (isClosing) {
            response.sendError(SC_SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }

        ReferenceMessageWriter messageWriter = new ReferenceMessageWriter(response);

        McpTransportContext transportContext = McpTransportContext.create(ImmutableMap.of(
                McpMetadata.CONTEXT_REQUEST_KEY, request,
                CONTEXT_MESSAGE_WRITER_KEY, messageWriter));

        String accept = request.getHeader(ACCEPT);
        if (accept == null || !(accept.contains(APPLICATION_JSON) && accept.contains(SERVER_SENT_EVENTS))) {
            responseError(response, SC_BAD_REQUEST, invalidRequest("Both application/json and text/event-stream required in Accept header"));
            return;
        }

        try (BufferedReader reader = request.getReader()) {
            // TODO: use Reader.readAllLines once on JDK 25
            String body = reader.lines().collect(Collectors.joining("\n"));

            JSONRPCMessage message = deserializeJsonRpcMessage(mcpJsonMapper, body);
            switch (message) {
                case JSONRPCRequest rpcRequest -> handleRpcRequest(transportContext, response, rpcRequest, messageWriter);
                case JSONRPCNotification rpcNotification -> handleRpcNotification(transportContext, response, rpcNotification);
                default -> responseError(response, SC_BAD_REQUEST, invalidRequest("The server accepts either requests or notifications"));
            }
        }
        catch (McpException | WebApplicationException exception) {
            throw exception;
        }
        catch (IllegalArgumentException | IOException e) {
            logger.error("Failed to deserialize message: {}", e.getMessage());
            responseError(response, SC_BAD_REQUEST, invalidRequest("Invalid message format"));
        }
        catch (Exception e) {
            logger.error("Unexpected error handling message: {}", e.getMessage());
            responseError(response, SC_INTERNAL_SERVER_ERROR, internalError("Unexpected error: " + e.getMessage()));
        }
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    private void handleRpcNotification(McpTransportContext transportContext, HttpServletResponse response, JSONRPCNotification rpcNotification)
    {
        Mono<Void> mono = switch (rpcNotification.method()) {
            case METHOD_NOTIFICATION_INITIALIZED -> Mono.empty();  // do nothing - just acknowledge
            default -> mcpHandler.handleNotification(transportContext, rpcNotification);
        };

        response.setStatus(SC_ACCEPTED);
        mono.contextWrite(ctx -> ctx.put(KEY, transportContext)).block();
    }

    private void handleRpcRequest(McpTransportContext transportContext, HttpServletResponse response, JSONRPCRequest rpcRequest, ReferenceMessageWriter messageWriter)
            throws IOException
    {
        Mono<JSONRPCResponse> rpcResponseMono = switch (rpcRequest.method()) {
            case METHOD_INITIALIZE -> sessionController.map(controller -> handleInitialize(controller, transportContext, rpcRequest)).orElseGet(() -> mcpHandler.handleRequest(transportContext, rpcRequest));
            case METHOD_LOGGING_SET_LEVEL -> sessionController.map(controller -> handleSetLoggingLevel(controller, transportContext, rpcRequest)).orElseGet(() -> mcpHandler.handleRequest(transportContext, rpcRequest));
            default -> handleMcpRequest(transportContext, rpcRequest);
        };

        JSONRPCResponse rpcResponse = rpcResponseMono.contextWrite(ctx -> ctx.put(KEY, transportContext)).block();

        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding(UTF_8.name());
        response.setStatus(SC_OK);

        messageWriter.write(mcpJsonMapper.writeValueAsString(rpcResponse));
        messageWriter.flushMessages();
    }

    private Mono<JSONRPCResponse> handleMcpRequest(McpTransportContext transportContext, JSONRPCRequest rpcRequest)
    {
        sessionController.ifPresent(controller -> {
            SessionId sessionId = requireSessionId(transportContext);
            if (!controller.validateSession(sessionId)) {
                throw new WebApplicationException(NOT_FOUND);
            }
        });

        return mcpHandler.handleRequest(transportContext, rpcRequest);
    }

    private Mono<JSONRPCResponse> handleSetLoggingLevel(SessionController sessionController, McpTransportContext transportContext, JSONRPCRequest rpcRequest)
    {
        SessionId sessionId = requireSessionId(transportContext);

        McpSchema.SetLevelRequest setLevelRequest = mcpJsonMapper.convertValue(rpcRequest.params(), McpSchema.SetLevelRequest.class);
        if (!sessionController.setSessionValue(sessionId, LOGGING_LEVEL, LoggingLevel.valueOf(setLevelRequest.level().name()))) {
            throw exception("Failed to set logging level for MCP session");
        }

        return just(new JSONRPCResponse(JSONRPC_VERSION, rpcRequest.id(), ImmutableMap.of(), null));
    }

    private Mono<JSONRPCResponse> handleInitialize(SessionController sessionController, McpTransportContext transportContext, JSONRPCRequest rpcRequest)
    {
        HttpServletRequest request = (HttpServletRequest) transportContext.get(McpMetadata.CONTEXT_REQUEST_KEY);
        HttpServletResponse response = (HttpServletResponse) request.getAttribute(HTTP_RESPONSE_ATTRIBUTE);

        SessionId sessionId = sessionController.createSession(request);
        response.addHeader(MCP_SESSION_ID, sessionId.id());
        sessionController.setSessionValue(sessionId, LOGGING_LEVEL, LoggingLevel.INFO);

        return mcpHandler.handleRequest(transportContext, rpcRequest);
    }

    private void responseError(HttpServletResponse response, int httpCode, McpError mcpError)
            throws IOException
    {
        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding(UTF_8);
        response.setStatus(httpCode);

        String jsonError = mcpJsonMapper.writeValueAsString(mcpError);
        PrintWriter writer = response.getWriter();
        writer.write(jsonError);

        writer.flush();
    }

    private static SessionId requireSessionId(McpTransportContext transportContext)
    {
        HttpServletRequest request = (HttpServletRequest) transportContext.get(McpMetadata.CONTEXT_REQUEST_KEY);
        return requireSessionId(request);
    }

    static SessionId requireSessionId(HttpServletRequest request)
    {
        String sessionId = Optional.ofNullable(request.getHeader(MCP_SESSION_ID))
                .orElseThrow(() -> exception("Missing MCP_SESSION_ID header in request"));
        return new SessionId(sessionId);
    }

    private static McpError invalidRequest(String message)
    {
        return new McpError(new JSONRPCError(INVALID_REQUEST, message, null));
    }

    private static McpError internalError(String message)
    {
        return new McpError(new JSONRPCError(INTERNAL_ERROR, message, null));
    }
}
