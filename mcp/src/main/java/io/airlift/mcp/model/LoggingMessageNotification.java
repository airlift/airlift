package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record LoggingMessageNotification(LoggingLevel level, String logger, Object data)
{
    public LoggingMessageNotification
    {
        requireNonNull(level, "level is null");
        requireNonNull(logger, "logger is null");
        requireNonNull(data, "data is null");
    }
}
