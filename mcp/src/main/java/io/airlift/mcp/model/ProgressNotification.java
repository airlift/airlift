package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record ProgressNotification(String progressToken, String message, Optional<Double> progress, Optional<Double> total)
{
    public ProgressNotification
    {
        requireNonNull(progressToken, "progressToken is null");
        requireNonNull(message, "message is null");
        requireNonNull(progress, "progress is null");
        requireNonNull(total, "total is null");
    }
}
