package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Task(String taskId, TaskStatus status, Optional<String> statusMessage, String createdAt, String lastUpdatedAt, OptionalInt ttlMs, OptionalInt pollIntervalMs, Optional<JsonRpcErrorDetail> error)
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
    }
}
