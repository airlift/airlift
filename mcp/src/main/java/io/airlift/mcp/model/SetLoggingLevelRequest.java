package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record SetLoggingLevelRequest(LoggingLevel level)
{
    public SetLoggingLevelRequest
    {
        requireNonNull(level, "level is null");
    }
}
