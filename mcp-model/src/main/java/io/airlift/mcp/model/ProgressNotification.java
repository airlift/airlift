package io.airlift.mcp.model;

import java.util.Optional;
import java.util.OptionalDouble;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record ProgressNotification(Optional<Object> progressToken, String message, OptionalDouble progress, OptionalDouble total)
{
    public ProgressNotification
    {
        progressToken = requireNonNullElse(progressToken, Optional.empty());
        requireNonNull(message, "message is null");
        progress = requireNonNullElse(progress, OptionalDouble.empty());
        total = requireNonNullElse(total, OptionalDouble.empty());
    }
}
