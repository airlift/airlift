package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record LoggingMessageNotification(LoggingLevel level, Optional<String> logger, Optional<Object> data)
{
    public LoggingMessageNotification
    {
        requireNonNull(level, "level is null");
        logger = requireNonNullElse(logger, Optional.empty());
        data = requireNonNullElse(data, Optional.empty());
    }
}
