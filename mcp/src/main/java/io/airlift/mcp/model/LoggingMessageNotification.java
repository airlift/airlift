package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record LoggingMessageNotification(LoggingLevel level, Optional<String> logger, Optional<Object> data)
{
    public LoggingMessageNotification
    {
        requireNonNull(level, "level is null");
        requireNonNull(logger, "logger is null");
        requireNonNull(data, "data is null");
    }
}
