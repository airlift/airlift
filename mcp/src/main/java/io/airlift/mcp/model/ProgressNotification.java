package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalDouble;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record ProgressNotification(Optional<Object> progressToken, String message, OptionalDouble progress, OptionalDouble total)
{
    public ProgressNotification
    {
        progressToken = firstNonNull(progressToken, Optional.empty());
        requireNonNull(message, "message is null");
        progress = firstNonNull(progress, OptionalDouble.empty());
        total = firstNonNull(total, OptionalDouble.empty());
    }
}
