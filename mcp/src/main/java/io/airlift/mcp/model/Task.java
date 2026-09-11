package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

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
        this(taskId, status, statusMessage, createdAt, lastUpdatedAt, ttlMs, pollIntervalMs, error, Optional.empty(), Optional.empty());
    }

    public Task asInputRequired(Optional<Map<String, InputRequest>> inputRequests)
    {
        return new Task(taskId, status, statusMessage, createdAt, lastUpdatedAt, ttlMs, pollIntervalMs, error, Optional.empty(), inputRequests);
    }
}
