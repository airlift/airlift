package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;

public record Task(String taskId, TaskStatus status, Optional<String> statusMessage, String createdAt, String lastUpdatedAt, OptionalInt ttlMs, OptionalInt pollIntervalMs, Optional<JsonRpcErrorDetail> error)
{
    public Task
    {
        requireNonNull(taskId, "taskId is null");
        requireNonNull(status, "status is null");
        requireNonNull(statusMessage, "statusMessage is null");
        requireNonNull(createdAt, "createdAt is null");
        requireNonNull(lastUpdatedAt, "lastUpdatedAt is null");
        requireNonNull(ttlMs, "ttlMs is null");
        requireNonNull(pollIntervalMs, "pollIntervalMs is null");
        requireNonNull(error, "error is null");
    }
}
