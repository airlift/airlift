package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record SetLevelRequest(LoggingLevel level)
{
    public SetLevelRequest
    {
        requireNonNull(level, "level is null");
    }
}
