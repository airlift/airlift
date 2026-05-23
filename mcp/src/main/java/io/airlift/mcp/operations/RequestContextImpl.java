package io.airlift.mcp.operations;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.messages.MessageWriter;
import io.airlift.mcp.model.InitializeRequest.ClientCapabilities;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.LoggingMessageNotification;
import io.airlift.mcp.model.ProgressNotification;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Optional;
import java.util.OptionalDouble;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.ACCEPTS_STREAMING_ATTRIBUTE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static io.airlift.mcp.model.JsonRpcRequest.buildNotification;
import static java.lang.Boolean.FALSE;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

class RequestContextImpl
        implements McpRequestContext
{
    private final HttpServletRequest request;
    private final RequestMetadata requestMetadata;
    private final JsonMapper jsonMapper;
    private final MessageWriter messageWriter;
    private final Authenticated<?> identity;

    RequestContextImpl(
            HttpServletRequest request,
            RequestMetadata requestMetadata,
            JsonMapper jsonMapper,
            MessageWriter messageWriter,
            Authenticated<?> identity)
    {
        this.request = requireNonNull(request, "request is null");
        this.requestMetadata = requireNonNull(requestMetadata, "requestMetadata is null");
        this.jsonMapper = requireNonNull(jsonMapper, "jsonMapper is null");
        this.messageWriter = requireNonNull(messageWriter, "messageWriter is null");
        this.identity = requireNonNull(identity, "identity is null");
    }

    @Override
    public HttpServletRequest request()
    {
        return request;
    }

    @Override
    public Authenticated<?> identity()
    {
        return identity;
    }

    @Override
    public void sendProgress(double progress, double total, String message)
    {
        if (!acceptsStreaming()) {
            return;
        }

        Optional<Object> appliedProgressToken = requestMetadata.progressToken().map(token -> switch (token) {
            case Number n -> Optional.of(n.longValue());
            default -> requestMetadata.progressToken();
        });

        ProgressNotification notification = new ProgressNotification(appliedProgressToken, message, OptionalDouble.of(progress), OptionalDouble.of(total));
        sendMessage(NOTIFICATION_PROGRESS, Optional.of(notification));
    }

    @Override
    public void sendMessage(String method, Optional<Object> params)
    {
        checkState(acceptsStreaming(), "Streaming is not supported by client connection");

        JsonRpcRequest<?> notification = params.map(p -> buildNotification(method, p)).orElseGet(() -> buildNotification(method));
        internalSendRequest(notification);
    }

    @Override
    public ClientCapabilities clientCapabilities()
    {
        return requestMetadata.clientCapabilities();
    }

    @Override
    public void sendLog(LoggingLevel level, Optional<String> logger, Optional<Object> data)
    {
        if (!acceptsStreaming()) {
            return;
        }

        requestMetadata.loggingLevel().ifPresent(requestLoggingLevel -> {
            if (level.level() >= requestLoggingLevel.level()) {
                LoggingMessageNotification logNotification = new LoggingMessageNotification(level, logger, data);
                sendMessage(NOTIFICATION_MESSAGE, Optional.of(logNotification));
            }
        });
    }

    private boolean acceptsStreaming()
    {
        return requireNonNullElse((Boolean) request.getAttribute(ACCEPTS_STREAMING_ATTRIBUTE), FALSE);
    }

    private void internalSendRequest(JsonRpcRequest<?> rpcRequest)
    {
        try {
            String json = jsonMapper.writeValueAsString(rpcRequest);
            messageWriter.writeMessage(json);
            messageWriter.flush();
        }
        catch (IOException e) {
            throw exception(e);
        }
    }
}
