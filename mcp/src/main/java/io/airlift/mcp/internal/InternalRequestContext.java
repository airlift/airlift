package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.MessageWriter;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.ProgressNotification;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalDouble;

import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static java.util.Objects.requireNonNull;

class InternalRequestContext
        implements McpRequestContext
{
    private final ObjectMapper objectMapper;
    private final HttpServletRequest request;
    private final MessageWriter messageWriter;
    private final Optional<Object> progressToken;

    InternalRequestContext(ObjectMapper objectMapper, HttpServletRequest request, MessageWriter messageWriter, Optional<Object> progressToken)
    {
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.request = requireNonNull(request, "request is null");
        this.messageWriter = requireNonNull(messageWriter, "messageWriter is null");
        this.progressToken = requireNonNull(progressToken, "progressToken is null");
    }

    @Override
    public HttpServletRequest request()
    {
        return request;
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
        internalSendMessage(NOTIFICATION_PROGRESS, Optional.of(notification));
    }

    @SuppressWarnings("SameParameterValue")
    private void internalSendMessage(String method, Optional<Object> params)
    {
        JsonRpcRequest<?> notification = params.map(p -> buildNotification(method, p)).orElseGet(() -> buildNotification(method));
        internalSendRequest(notification);
    }

    private void internalSendRequest(JsonRpcRequest<?> rpcRequest)
    {
        try {
            String json = objectMapper.writeValueAsString(rpcRequest);
            messageWriter.writeMessage(json);
            messageWriter.flushMessages();
        }
        catch (IOException e) {
            throw exception(e);
        }
    }
}
