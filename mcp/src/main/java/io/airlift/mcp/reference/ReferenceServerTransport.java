package io.airlift.mcp.reference;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpMetadata;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse.JSONRPCError;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Collectors;

import static io.airlift.mcp.McpMetadata.CONTEXT_REQUEST_KEY;
import static io.modelcontextprotocol.common.McpTransportContext.KEY;
import static io.modelcontextprotocol.spec.McpSchema.ErrorCodes.INTERNAL_ERROR;
import static io.modelcontextprotocol.spec.McpSchema.ErrorCodes.INVALID_REQUEST;
import static io.modelcontextprotocol.spec.McpSchema.deserializeJsonRpcMessage;
import static jakarta.servlet.http.HttpServletResponse.SC_ACCEPTED;
import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static jakarta.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static jakarta.ws.rs.core.HttpHeaders.ACCEPT;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

// based off of io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport.java
public class ReferenceServerTransport
        extends HttpServlet
        implements McpStatelessServerTransport
{
    private static final Logger logger = Logger.get(ReferenceServerTransport.class);

    private final McpJsonMapper mcpJsonMapper;
    private final String mcpEndpoint;
    private volatile McpStatelessServerHandler mcpHandler;
    private volatile boolean isClosing;

    @Inject
    public ReferenceServerTransport(McpMetadata metadata, McpJsonMapper mcpJsonMapper)
    {
        this.mcpJsonMapper = requireNonNull(mcpJsonMapper, "jsonMapper is null");
        this.mcpEndpoint = metadata.uriPath();
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
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        boolean isMcpEndpoint = request.getRequestURI().endsWith(mcpEndpoint);
        response.sendError(isMcpEndpoint ? SC_METHOD_NOT_ALLOWED : SC_NOT_FOUND);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException
    {
        String requestURI = request.getRequestURI();
        if (!requestURI.endsWith(mcpEndpoint)) {
            response.sendError(SC_NOT_FOUND);
            return;
        }

        if (isClosing) {
            response.sendError(SC_SERVICE_UNAVAILABLE, "Server is shutting down");
            return;
        }

        McpTransportContext transportContext = McpTransportContext.create(ImmutableMap.of(CONTEXT_REQUEST_KEY, request));

        String accept = request.getHeader(ACCEPT);
        if (accept == null || !(accept.contains(APPLICATION_JSON) && accept.contains(SERVER_SENT_EVENTS))) {
            responseError(response, SC_BAD_REQUEST, invalidRequest("Both application/json and text/event-stream required in Accept header"));
            return;
        }

        try {
            BufferedReader reader = request.getReader();
            String body = reader.lines().collect(Collectors.joining("\n"));

            JSONRPCMessage message = deserializeJsonRpcMessage(mcpJsonMapper, body);
            switch (message) {
                case JSONRPCRequest rpcRequest -> handleRpcRequest(transportContext, response, rpcRequest);
                case JSONRPCNotification jsonrpcNotification -> handleRpcNotification(transportContext, response, jsonrpcNotification);
                default -> responseError(response, SC_BAD_REQUEST, invalidRequest("The server accepts either requests or notifications"));
            }
        }
        catch (McpException mcpException) {
            throw mcpException;
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

    private void handleRpcNotification(McpTransportContext transportContext, HttpServletResponse response, JSONRPCNotification jsonrpcNotification)
    {
        mcpHandler.handleNotification(transportContext, jsonrpcNotification)
                .contextWrite(ctx -> ctx.put(KEY, transportContext))
                .block();
        response.setStatus(SC_ACCEPTED);
    }

    private void handleRpcRequest(McpTransportContext transportContext, HttpServletResponse response, JSONRPCRequest rpcRequest)
            throws IOException
    {
        JSONRPCResponse rpcResponse = mcpHandler
                .handleRequest(transportContext, rpcRequest)
                .contextWrite(ctx -> ctx.put(KEY, transportContext))
                .block();

        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding(UTF_8.name());
        response.setStatus(SC_OK);

        String jsonResponseText = mcpJsonMapper.writeValueAsString(rpcResponse);

        PrintWriter writer = response.getWriter();
        writer.write(jsonResponseText);
        writer.flush();
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

    private static McpError invalidRequest(String message)
    {
        return new McpError(new JSONRPCError(INVALID_REQUEST, message, null));
    }

    private static McpError internalError(String message)
    {
        return new McpError(new JSONRPCError(INTERNAL_ERROR, message, null));
    }
}
