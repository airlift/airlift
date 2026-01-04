package io.airlift.mcp.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.collect.Maps.filterKeys;
import static io.airlift.mcp.model.TaskStatus.CANCELLED;
import static io.airlift.mcp.model.TaskStatus.COMPLETED;
import static io.airlift.mcp.model.TaskStatus.FAILED;
import static io.airlift.mcp.model.TaskStatus.INPUT_REQUIRED;
import static io.airlift.mcp.model.TaskStatus.WORKING;
import static java.util.Objects.requireNonNull;

public record TaskFacade(
        String taskId,
        Instant createdAt,
        Instant lastUpdatedAt,
        Object requestId,
        Optional<Object> progressToken,
        Optional<Instant> completedAt,
        int pollIntervalMs,
        Optional<String> statusMessage,
        int ttlMs,
        Optional<JsonRpcMessage> message,
        boolean cancellationRequested,
        Map<Object, JsonRpcResponse<?>> responses,
        Map<String, String> attributes)
{
    public TaskFacade
    {
        requireNonNull(taskId, "taskId is null");
        requireNonNull(createdAt, "createdAt is null");
        requireNonNull(lastUpdatedAt, "lastUpdatedAt is null");
        requireNonNull(requestId, "requestId is null");
        requireNonNull(progressToken, "progressToken is null");
        requireNonNull(completedAt, "completedAt is null");
        requireNonNull(statusMessage, "statusMessage is null");
        requireNonNull(message, "message is null");

        responses = ImmutableMap.copyOf(responses);
        attributes = ImmutableMap.copyOf(attributes);
    }

    public TaskFacade(String taskId, Instant now, Object requestId, Optional<Object> progressToken, int pollInterval, int ttl)
    {
        this(taskId, now, now, requestId, progressToken, Optional.empty(), pollInterval, Optional.empty(), ttl, Optional.empty(), false, ImmutableMap.of(), ImmutableMap.of());
    }

    public TaskStatus toTaskStatus()
    {
        if (completedAt.isPresent()) {
            if (cancellationRequested) {
                return CANCELLED;
            }

            return switch (message.orElse(null)) {
                case JsonRpcResponse<?> response when response.error().isPresent() -> FAILED;
                case JsonRpcResponse<?> _ -> COMPLETED;
                case null, default -> TaskStatus.COMPLETED;
            };
        }

        return message.isPresent() ? INPUT_REQUIRED : WORKING;
    }

    public Task toTask()
    {
        return new Task(
                createdAt.toString(),
                lastUpdatedAt.toString(),
                OptionalInt.of(pollIntervalMs),
                toTaskStatus(),
                statusMessage,
                taskId,
                ttlMs);
    }

    public TaskFacade withMessage(JsonRpcMessage message, Optional<String> statusMessage)
    {
        return new TaskFacade(
                taskId,
                createdAt,
                Instant.now(),
                requestId,
                progressToken,
                completedAt,
                pollIntervalMs,
                statusMessage,
                ttlMs,
                Optional.of(message),
                cancellationRequested,
                responses,
                attributes);
    }

    public TaskFacade withoutMessage()
    {
        return new TaskFacade(
                taskId,
                createdAt,
                Instant.now(),
                requestId,
                progressToken,
                completedAt,
                pollIntervalMs,
                statusMessage,
                ttlMs,
                Optional.empty(),
                cancellationRequested,
                responses,
                attributes);
    }

    public TaskFacade asCompleted()
    {
        return new TaskFacade(
                taskId,
                createdAt,
                Instant.now(),
                requestId,
                progressToken,
                Optional.of(Instant.now()),
                pollIntervalMs,
                statusMessage,
                ttlMs,
                message,
                cancellationRequested,
                responses,
                attributes);
    }

    public TaskFacade withCancellationRequested(Optional<String> statusMessage)
    {
        return new TaskFacade(
                taskId,
                createdAt,
                Instant.now(),
                requestId,
                progressToken,
                completedAt,
                pollIntervalMs,
                statusMessage,
                ttlMs,
                message,
                true,
                responses,
                attributes);
    }

    public TaskFacade withResponse(JsonRpcResponse<?> response)
    {
        ImmutableMap<Object, JsonRpcResponse<?>> updatedResponses = ImmutableMap.<Object, JsonRpcResponse<?>>builder()
                .putAll(responses)
                .put(response.id(), response)
                .build();

        Optional<JsonRpcMessage> updatedMessage = message.flatMap(localMessage -> {
            if (response.id().equals(localMessage.id())) {
                // the outgoing message has been responded to, clear it
                return Optional.empty();
            }
            return Optional.of(localMessage);
        });

        return new TaskFacade(
                taskId,
                createdAt,
                Instant.now(),
                requestId,
                progressToken,
                completedAt,
                pollIntervalMs,
                statusMessage,
                ttlMs,
                updatedMessage,
                cancellationRequested,
                updatedResponses,
                attributes);
    }

    public TaskFacade withAttribute(String key, String value)
    {
        Map<String, String> updatedAttributes = ImmutableMap.<String, String>builder()
                .putAll(attributes)
                .put(key, value)
                .build();

        return new TaskFacade(
                taskId,
                createdAt,
                Instant.now(),
                requestId,
                progressToken,
                completedAt,
                pollIntervalMs,
                statusMessage,
                ttlMs,
                message,
                cancellationRequested,
                responses,
                updatedAttributes);
    }

    public TaskFacade withoutAttribute(String key)
    {
        Map<String, String> updatedAttributes = filterKeys(attributes, checkKey -> !checkKey.equals(key));

        return new TaskFacade(
                taskId,
                createdAt,
                Instant.now(),
                requestId,
                progressToken,
                completedAt,
                pollIntervalMs,
                statusMessage,
                ttlMs,
                message,
                cancellationRequested,
                responses,
                updatedAttributes);
    }

    public <R> Optional<R> extractResponse(Object requestId, Class<R> responseType, ObjectMapper objectMapper)
    {
        return Optional.ofNullable(responses.get(requestId))
                .flatMap(JsonRpcResponse::result)
                .map(value -> objectMapper.convertValue(value, responseType));
    }
}
