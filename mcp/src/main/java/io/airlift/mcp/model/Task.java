package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;

public record Task(String taskId, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC") Instant createdAt, OptionalInt pollInterval, TaskStatus status, Optional<String> statusMessage, OptionalInt ttl)
{
    public static final String META_KEY_RELATED_TASK = "io.modelcontextprotocol/related-task";

    public Task
    {
        requireNonNull(taskId, "taskId is null");
        requireNonNull(createdAt, "createdAt is null");
        requireNonNull(status, "status is null");
        requireNonNull(statusMessage, "statusMessage is null");
        requireNonNull(pollInterval, "pollInterval is null");
        requireNonNull(ttl, "ttl is null");
    }

    public Task withStatus(TaskStatus status, Optional<String> statusMessage)
    {
        return new Task(taskId, createdAt, pollInterval, status, statusMessage, ttl);
    }
}
