package io.airlift.mcp.reference;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.log.Logger;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpServer;
import io.airlift.mcp.model.Implementation;
import io.airlift.mcp.model.InitializeRequest;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.ListRootsResult;
import io.airlift.mcp.model.ListRootsResult.Root;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.ProgressNotification;
import io.airlift.mcp.model.RootsList;
import io.airlift.mcp.session.McpSessionController;
import io.airlift.mcp.session.McpValueKey;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Supplier;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.reference.Mapper.unmapLoggingLevel;
import static io.airlift.mcp.reference.SessionHandlerAndTransport.requireSessionId;
import static io.airlift.mcp.session.McpValueKey.CLIENT_CAPABILITIES;
import static io.airlift.mcp.session.McpValueKey.CLIENT_INFO;
import static io.airlift.mcp.session.McpValueKey.LOGGING_LEVEL;
import static io.airlift.mcp.session.McpValueKey.RESPONSE;
import static io.airlift.mcp.session.McpValueKey.ROOTS;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_NOTIFICATION_MESSAGE;
import static io.modelcontextprotocol.spec.McpSchema.METHOD_ROOTS_LIST;
import static java.util.Objects.requireNonNull;

class ReferenceRequestContext
        implements McpRequestContext
{
    private static final Logger log = Logger.get(ReferenceRequestContext.class);

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final McpServer server;
    private final Optional<McpSessionController> sessionController;
    private final Optional<Object> progressToken;
    private final ObjectMapper objectMapper;

    ReferenceRequestContext(
            HttpServletRequest request,
            HttpServletResponse response,
            McpServer server,
            Optional<McpSessionController> sessionController,
            Optional<Object> progressToken,
            ObjectMapper objectMapper)
    {
        this.request = requireNonNull(request, "request is null");
        this.response = requireNonNull(response, "response is null");
        this.server = requireNonNull(server, "mcpServer is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.progressToken = requireNonNull(progressToken, "progressToken is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
    }

    @Override
    public McpServer server()
    {
        return server;
    }

    @Override
    public HttpServletRequest request()
    {
        return request;
    }

    @Override
    public InitializeRequest.ClientCapabilities clientCapabilities()
    {
        McpSessionController localSessionController = sessionController.orElseThrow(() -> new UnsupportedOperationException("Sessions are not enabled"));
        String sessionId = requireSessionId(request);

        return localSessionController.currentValue(sessionId, CLIENT_CAPABILITIES).orElseThrow(() -> new UnsupportedOperationException("session id not found"));
    }

    @Override
    public Implementation clientInfo()
    {
        McpSessionController localSessionController = sessionController.orElseThrow(() -> new UnsupportedOperationException("Sessions are not enabled"));
        String sessionId = requireSessionId(request);

        return localSessionController.currentValue(sessionId, CLIENT_INFO).orElseThrow(() -> new UnsupportedOperationException("session id not found"));
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    @Override
    public void sendProgress(double progress, double total, String message)
    {
        Optional<Object> appliedProgressToken = progressToken.map(token -> switch (token) {
            case Number n -> Optional.of(n.longValue());
            default -> progressToken;
        });

        ProgressNotification notification = new ProgressNotification(appliedProgressToken, message, OptionalDouble.of(progress), OptionalDouble.of(total));
        sendNotification(JsonRpcRequest.buildNotification("notifications/progress", notification));
    }

    @Override
    public <T, R> R serverToClientRequest(String method, T params, TypeReference<R> responseType, Duration timeout)
            throws McpException
    {
        McpSessionController localSessionController = sessionController.orElseThrow(() -> new UnsupportedOperationException("Sessions are not enabled"));
        String sessionId = requireSessionId(request);

        String requestId = UUID.randomUUID().toString();
        McpValueKey<JsonRpcMessage> responseKey = RESPONSE.withSuffix(requestId);

        try {
            JsonRpcRequest<T> rpcRequest = JsonRpcRequest.buildRequest(requestId, method, params);
            sendNotification(rpcRequest);

            Supplier<Optional<JsonRpcResponse<?>>> conditionSupplier = () -> localSessionController.currentValue(sessionId, responseKey)
                    .flatMap(message -> (message instanceof JsonRpcResponse<?> rpcResponse) ? Optional.of(rpcResponse) : Optional.empty());
            JsonRpcResponse<?> ourResponse = localSessionController.waitValueCondition(sessionId, conditionSupplier, timeout)
                    .orElseThrow(() -> exception("Timeout waiting for response to request"));

            return ourResponse.error().map(error -> {
                throw McpException.exception(error.code(), error.message(), error.data());
            }).or(ourResponse::result).map(result -> objectMapper.convertValue(result, responseType)).orElseThrow(() -> exception("No result returned"));
        }
        finally {
            localSessionController.deleteValue(sessionId, responseKey);
        }
    }

    @Override
    public List<Root> roots(Duration timeout)
    {
        McpSessionController localSessionController = sessionController.orElseThrow(() -> new UnsupportedOperationException("Sessions are not enabled"));
        String sessionId = requireSessionId(request);

        return localSessionController.currentValue(sessionId, ROOTS)
                .map(RootsList::roots)
                .orElseGet(() -> requestRoots(timeout));
    }

    private List<Root> requestRoots(Duration timeout)
    {
        ListRootsResult listRootsResult = serverToClientRequest(METHOD_ROOTS_LIST, Optional.empty(), new TypeReference<>() {}, timeout);

        McpSessionController localSessionController = sessionController.orElseThrow(() -> new UnsupportedOperationException("Sessions are not enabled"));
        String sessionId = requireSessionId(request);
        localSessionController.upsertValue(sessionId, ROOTS, new RootsList(listRootsResult.roots()));

        return listRootsResult.roots();
    }

    @Override
    public void sendLog(LoggingLevel level, String name, Object messageOrData)
    {
        McpSessionController localSessionController = sessionController.orElseThrow(() -> new UnsupportedOperationException("Sessions are not enabled"));
        LoggingLevel sessionLevel = localSessionController.currentValue(requireSessionId(request), LOGGING_LEVEL).orElseThrow(() -> exception("session id not found"));
        if (sessionLevel.level() > level.level()) {
            return;
        }

        try {
            String appliedMessage = (messageOrData instanceof String message) ? message : objectMapper.writeValueAsString(messageOrData);
            JsonRpcRequest<McpSchema.LoggingMessageNotification> notification = JsonRpcRequest.buildNotification(METHOD_NOTIFICATION_MESSAGE, new McpSchema.LoggingMessageNotification(unmapLoggingLevel(level), name, appliedMessage));
            sendNotification(notification);
        }
        catch (JsonProcessingException e) {
            log.error(e, "Failed to serialize notification");
            throw exception(e);
        }
    }

    private void sendNotification(JsonRpcRequest<?> message)
    {
        try {
            PrintWriter writer = response.getWriter();
            if (!(writer instanceof SsePrintWriter ssePrintWriter)) {
                throw exception("Response writer is not an SsePrintWriter");
            }
            String json = objectMapper.writeValueAsString(message);
            ssePrintWriter.writeMessage(json);
            ssePrintWriter.flush();
        }
        catch (IOException e) {
            log.error(e, "Error sending notification");
            throw exception(e);
        }
    }
}
