package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static io.airlift.mcp.model.ResultType.COMPLETE;
import static io.airlift.mcp.model.ResultType.TASK;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Task(
        String taskId,
        TaskStatus status,
        Optional<String> statusMessage,
        String createdAt,
        String lastUpdatedAt,
        OptionalInt ttlMs,
        OptionalInt pollIntervalMs,
        Optional<JsonRpcErrorDetail> error,
        ResultType resultType,
        Optional<CallToolResult> result,
        Optional<Map<String, InputRequest>> inputRequests)
        implements ToolResult
{
    public Task
    {
        requireNonNull(taskId, "taskId is null");
        requireNonNull(status, "status is null");
        statusMessage = requireNonNullElse(statusMessage, Optional.empty());
        requireNonNull(createdAt, "createdAt is null");
        requireNonNull(lastUpdatedAt, "lastUpdatedAt is null");
        ttlMs = requireNonNullElse(ttlMs, OptionalInt.empty());
        pollIntervalMs = requireNonNullElse(pollIntervalMs, OptionalInt.empty());
        error = requireNonNullElse(error, Optional.empty());
        requireNonNull(resultType, "resultType is null");
        result = requireNonNullElse(result, Optional.empty());
        inputRequests = requireNonNullElse(inputRequests, Optional.empty());
    }

    public Task(
            String taskId,
            TaskStatus status,
            Optional<String> statusMessage,
            String createdAt,
            String lastUpdatedAt,
            OptionalInt ttlMs,
            OptionalInt pollIntervalMs,
            Optional<JsonRpcErrorDetail> error)
    {
        this(taskId, status, statusMessage, createdAt, lastUpdatedAt, ttlMs, pollIntervalMs, error, TASK, Optional.empty(), Optional.empty());
    }

    public Task asComplete()
    {
        return new Task(taskId, status, statusMessage, createdAt, lastUpdatedAt, ttlMs, pollIntervalMs, error, COMPLETE, Optional.empty(), Optional.empty());
    }

    public Task asComplete(Optional<CallToolResult> result)
    {
        return new Task(taskId, status, statusMessage, createdAt, lastUpdatedAt, ttlMs, pollIntervalMs, error, COMPLETE, result, Optional.empty());
    }

    public Task asInputRequired(Optional<Map<String, InputRequest>> inputRequests)
    {
        return new Task(taskId, status, statusMessage, createdAt, lastUpdatedAt, ttlMs, pollIntervalMs, error, COMPLETE, Optional.empty(), inputRequests);
    }
}
