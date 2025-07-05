package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.jsonrpc.model.JsonRpcErrorDetail;
import io.airlift.jsonrpc.model.JsonRpcRequest;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.ProgressNotification;
import jakarta.ws.rs.core.Request;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static io.airlift.jsonrpc.model.JsonRpcRequest.buildNotification;
import static io.airlift.jsonrpc.model.JsonRpcResponse.buildErrorResponse;
import static io.airlift.jsonrpc.model.JsonRpcResponse.buildResponse;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

class InternalNotifier
        implements McpNotifier
{
    private final Request request;
    private final ObjectMapper objectMapper;
    private final String progressToken;
    private final AtomicLong eventId;
    private final Writer writer;

    InternalNotifier(Request request, ObjectMapper objectMapper, Meta meta, OutputStream output)
    {
        this.request = requireNonNull(request, "request is null");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper is null");
        this.eventId = new AtomicLong();
        this.writer = new OutputStreamWriter(output, UTF_8);

        progressToken = meta.meta()
                .flatMap(m -> Optional.ofNullable(m.get("progressToken")).map(String::valueOf))
                .orElse("unknown");
    }

    @Override
    public void notifyProgress(String message, Optional<Double> progress, Optional<Double> total)
    {
        ProgressNotification progressNotification = new ProgressNotification(progressToken, message, progress, total);
        JsonRpcRequest<ProgressNotification> notification = buildNotification(NOTIFICATION_PROGRESS, progressNotification);
        writeEvent(notification);
    }

    @Override
    public <T> void sendNotification(String notificationType, T data)
    {
        JsonRpcRequest<?> notification = buildNotification(notificationType, data);
        writeEvent(notification);
    }

    @Override
    public void sendNotification(String notificationType)
    {
        JsonRpcRequest<?> notification = buildNotification(notificationType);
        writeEvent(notification);
    }

    void writeResult(Object data)
    {
        JsonRpcResponse<Object> response = buildResponse(request, data);
        writeEvent(response);
    }

    void writeError(JsonRpcErrorDetail errorDetail)
    {
        JsonRpcResponse<Object> response = buildErrorResponse(request, errorDetail);
        writeEvent(response);
    }

    private void writeEvent(Object data)
    {
        try {
            writer.write("id: event-%s%n".formatted(eventId.getAndIncrement()));
            writer.write("data: %s%n".formatted(encode(objectMapper.writeValueAsString(data))));
            writer.write('\n');
            writer.flush();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String encode(String str)
    {
        // Escape newlines and carriage returns for SSE compliance
        return str.replace("\n", "\\n").replace("\r", "\\r");
    }
}
