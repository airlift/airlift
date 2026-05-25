package io.airlift.mcp.internal;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

// public for Jackson
public record InternalTaskExecution(UUID executionId, Instant lastUpdate)
{
    public InternalTaskExecution
    {
        requireNonNull(executionId, "executionId is null");
        requireNonNull(lastUpdate, "lastUpdate is null");
    }

    boolean hasExpired(Duration staleExecutionThreshold)
    {
        Duration elapsed = Duration.between(lastUpdate, Instant.now());
        return elapsed.compareTo(staleExecutionThreshold) > 0;
    }
}
