package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalDouble;

import static java.util.Objects.requireNonNull;

public record ProgressNotification(Optional<Object> progressToken, String message, OptionalDouble progress, OptionalDouble total)
{
    public ProgressNotification
    {
        requireNonNull(progressToken, "progressToken is null");
        requireNonNull(message, "message is null");
        requireNonNull(progress, "progress is null");
        requireNonNull(total, "total is null");
    }
}
