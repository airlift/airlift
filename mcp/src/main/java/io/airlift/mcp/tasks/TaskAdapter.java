package io.airlift.mcp.tasks;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record TaskAdapter(
        String taskId,
        Instant createdAt,
        Instant lastUpdatedAt,
        Object requestId,
        Map<String, String> attributes,
        Optional<Object> progressToken,
        Optional<Instant> completeAt,
        OptionalInt pollInterval,
        Optional<String> statusMessage,
        OptionalInt ttl,
        Optional<JsonRpcMessage> message,
        boolean cancellationRequested,
        Map<Object, JsonRpcResponse<?>> responses)
{
    public TaskAdapter
    {
        requireNonNull(taskId, "taskId is null");
        requireNonNull(createdAt, "createdAt is null");
        requireNonNull(lastUpdatedAt, "lastUpdatedAt is null");
        requireNonNull(requestId, "requestId is null");
        attributes = ImmutableMap.copyOf(attributes);
        requireNonNull(progressToken, "progressToken is null");
        requireNonNull(completeAt, "completeAt is null");
        requireNonNull(pollInterval, "pollInterval is null");
        requireNonNull(statusMessage, "statusMessage is null");
        requireNonNull(ttl, "ttl is null");
        requireNonNull(message, "message is null");

        responses = ImmutableMap.copyOf(responses);

        boolean hasMessage = message.isPresent();
        boolean isError = isErrorMessage(message);
        boolean isComplete = completeAt.isPresent();

        checkArgument(!cancellationRequested || hasMessage, "cancellationRequested can only be true when there is a message");
        checkArgument(!isError || isComplete, "error messages can only be set when the task is complete");
    }

    public TaskAdapter(String taskId, Instant now, Object requestId, Map<String, String> attributes, Optional<Object> progressToken, OptionalInt pollInterval, OptionalInt ttl)
    {
        this(taskId, now, now, requestId, attributes, progressToken, Optional.empty(), pollInterval, Optional.empty(), ttl, Optional.empty(), false, ImmutableMap.of());
    }

    public TaskAdapter withResponse(JsonRpcResponse<?> response)
    {
        ImmutableMap<Object, JsonRpcResponse<?>> updatedResponses = ImmutableMap.<Object, JsonRpcResponse<?>>builder()
                .putAll(responses)
                .put(response.id(), response)
                .buildKeepingLast();

        return new TaskAdapter(
                taskId,
                createdAt,
                lastUpdatedAt,
                requestId,
                attributes,
                progressToken,
                completeAt,
                pollInterval,
                statusMessage,
                ttl,
                message,
                cancellationRequested,
                updatedResponses);
    }

    public TaskAdapter withoutMessage()
    {
        return new TaskAdapter(
                taskId,
                createdAt,
                lastUpdatedAt,
                requestId,
                attributes,
                progressToken,
                completeAt,
                pollInterval,
                statusMessage,
                ttl,
                Optional.empty(),
                cancellationRequested,
                responses);
    }

    public Task toTask()
    {
        return new Task(
                createdAt.toString(),
                lastUpdatedAt.toString(),
                pollInterval,
                toTaskStatus(),
                statusMessage,
                taskId,
                ttl);
    }

    public TaskStatus toTaskStatus()
    {
        if (completeAt.isPresent()) {
            if (cancellationRequested) {
                return TaskStatus.CANCELLED;
            }

            return isErrorMessage(message) ? TaskStatus.FAILED : TaskStatus.COMPLETED;
        }

        return message.isPresent() ? TaskStatus.INPUT_REQUIRED : TaskStatus.WORKING;
    }

    public TaskAdapter withAttributes(Map<String, String> attributes)
    {
        return new TaskAdapter(
                taskId,
                createdAt,
                lastUpdatedAt,
                requestId,
                attributes,
                progressToken,
                completeAt,
                pollInterval,
                statusMessage,
                ttl,
                message,
                cancellationRequested,
                responses);
    }

    static boolean isErrorMessage(Optional<JsonRpcMessage> message)
    {
        return message.map(rpcMessage -> (rpcMessage instanceof JsonRpcResponse<?> response) && response.error().isPresent())
                .orElse(false);
    }
}
