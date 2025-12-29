package io.airlift.mcp.model;

import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record LoggingMessageNotification(LoggingLevel level, Optional<String> logger, Optional<Object> data)
{
    public LoggingMessageNotification
    {
        requireNonNull(level, "level is null");
        logger = firstNonNull(logger, Optional.empty());
        data = firstNonNull(data, Optional.empty());
    }
}
