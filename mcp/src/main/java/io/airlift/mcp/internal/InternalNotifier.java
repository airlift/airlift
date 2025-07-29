package io.airlift.mcp.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.jsonrpc.model.JsonRpcErrorDetail;
import io.airlift.jsonrpc.model.JsonRpcRequest;
import io.airlift.jsonrpc.model.JsonRpcResponse;
import io.airlift.log.Logger;
import io.airlift.mcp.McpNotifier;
import io.airlift.mcp.model.LoggingLevel;
import io.airlift.mcp.model.LoggingMessageNotification;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.ProgressNotification;
import io.airlift.mcp.session.RequestState;
import io.airlift.mcp.session.SessionController;
import io.airlift.mcp.session.SessionId;
import jakarta.ws.rs.core.Request;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.MoreObjects.firstNonNull;
import static io.airlift.jsonrpc.model.JsonRpcRequest.buildNotification;
import static io.airlift.jsonrpc.model.JsonRpcResponse.buildErrorResponse;
import static io.airlift.jsonrpc.model.JsonRpcResponse.buildResponse;
import static io.airlift.mcp.internal.InternalRpcMethods.currentRequestId;
import static io.airlift.mcp.internal.InternalSessionResource.SERVER_TO_CLIENT_ID_SIGNIFIER;
import static io.airlift.mcp.model.Constants.NOTIFICATION_MESSAGE;
import static io.airlift.mcp.model.Constants.NOTIFICATION_PROGRESS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

class InternalNotifier
        implements McpNotifier
{
    private static final Logger log = Logger.get(InternalNotifier.class);

    private final Request request;
    private final Optional<SessionController> sessionController;
    private final SessionId sessionId;
    private final ObjectMapper objectMapper;
    private final String progressToken;
    private final AtomicLong eventId;
    private final Writer writer;

    InternalNotifier(Request request, Optional<SessionController> sessionController, SessionId sessionId, ObjectMapper objectMapper, Meta meta, OutputStream output)
    {
        this.request = requireNonNull(request, "request is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.sessionId = requireNonNull(sessionId, "sessionId is null");
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

    @Override
    public <T> void sendLog(LoggingLevel level, String logger, T data)
    {
        writeLog(level, logger, Optional.of(data));
    }

    @Override
    public void sendLog(LoggingLevel level, String logger)
    {
        writeLog(level, logger, Optional.empty());
    }

    @Override
    public <T> void sendRequest(JsonRpcRequest<T> request)
    {
        // NOTE: sendRequest is for session-based client-to-server requests where a response is expected
        // writeRequest is context-free. It merely writes any request.

        Object givenId = firstNonNull(request.id(), "");
        String appliedId = SERVER_TO_CLIENT_ID_SIGNIFIER + givenId;

        JsonRpcRequest<?> appliedRequest = request.params()
                .map(params -> JsonRpcRequest.buildRequest(appliedId, request.method(), params))
                .orElseGet(() -> JsonRpcRequest.buildRequest(appliedId, request.method()));

        writeEvent(appliedRequest);
    }

    @Override
    public boolean cancellationRequested()
    {
        RequestState requestState = sessionController.flatMap(controller -> currentRequestId(request).map(requestId -> controller.requestState(sessionId, String.valueOf(requestId))))
                .orElse(RequestState.ENDED);
        return requestState == RequestState.CANCELLATION_REQUESTED;
    }

    <T> void writeRequest(JsonRpcRequest<T> request)
    {
        writeEvent(request);
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

    long nextEventId()
    {
        return eventId.getAndIncrement();
    }

    private void writeLog(LoggingLevel level, String logger, Optional<Object> maybeData)
    {
        sessionController.ifPresentOrElse(controller -> {
            try {
                LoggingLevel sessionLoggingLevel = controller.loggingLevel(sessionId);
                if (sessionLoggingLevel.ordinal() <= level.ordinal()) {
                    LoggingMessageNotification loggingMessageNotification = new LoggingMessageNotification(level, logger, maybeData);
                    sendNotification(NOTIFICATION_MESSAGE, loggingMessageNotification);
                }
            }
            catch (Exception e) {
                log.error(e, "Failed to read session state for session %s while logging: %s", sessionId, logger);
            }
        }, () -> log.debug("Sessions are not supported by this server, logging will not be recorded: %s", logger));
    }

    private void writeEvent(Object data)
    {
        try {
            writer.write("id: event-%s%n".formatted(nextEventId()));
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
