package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

import java.util.OptionalDouble;

public record ProgressNotification(
        String progressToken, String message, OptionalDouble progress, OptionalDouble total) {
    public ProgressNotification {
        requireNonNull(progressToken, "progressToken is null");
        requireNonNull(message, "message is null");
        requireNonNull(progress, "progress is null");
        requireNonNull(total, "total is null");
    }
}
