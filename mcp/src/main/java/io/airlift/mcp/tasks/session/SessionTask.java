package io.airlift.mcp.tasks.session;

import io.airlift.mcp.tasks.TaskResult;

import java.util.Optional;
import java.util.OptionalInt;

import static java.util.Objects.requireNonNull;

public record SessionTask(OptionalInt ttlMs, Optional<TaskResult> result)
{
    public SessionTask
    {
        // don't copy any collections
        requireNonNull(ttlMs, "ttlMs is null");
        requireNonNull(result, "result is null");
    }

    public SessionTask(OptionalInt ttlMs)
    {
        this(ttlMs, Optional.empty());
    }

    public SessionTask withResult(TaskResult taskResult)
    {
        return new SessionTask(ttlMs, Optional.of(taskResult));
    }
}
