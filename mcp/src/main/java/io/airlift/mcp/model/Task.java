package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Task(String createdAt, String lastUpdatedAt, OptionalInt pollInterval, TaskStatus status, Optional<String> statusMessage, String taskId, int ttl)
{
    public Task
    {
        requireNonNull(createdAt, "createdAt is null");
        requireNonNull(lastUpdatedAt, "lastUpdatedAt is null");
        pollInterval = requireNonNullElse(pollInterval, OptionalInt.empty());
        requireNonNull(status, "status is null");
        statusMessage = requireNonNullElse(statusMessage, Optional.empty());
        requireNonNull(taskId, "taskId is null");
    }
}
