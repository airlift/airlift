package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record Task(String createdAt, String lastUpdatedAt, OptionalInt pollInterval, TaskStatus status, Optional<String> statusMessage, String taskId, OptionalInt ttl)
        implements TaskMetadata
{
    public Task
    {
        requireNonNull(createdAt, "createdAt is null");
        requireNonNull(lastUpdatedAt, "lastUpdatedAt is null");
        pollInterval = firstNonNull(pollInterval, OptionalInt.empty());
        requireNonNull(status, "status is null");
        statusMessage = firstNonNull(statusMessage, Optional.empty());
        requireNonNull(taskId, "taskId is null");
        ttl = firstNonNull(ttl, OptionalInt.empty());
    }
}
