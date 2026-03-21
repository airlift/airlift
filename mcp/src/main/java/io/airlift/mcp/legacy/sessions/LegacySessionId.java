package io.airlift.mcp.legacy.sessions;

import static java.util.Objects.requireNonNull;

public record LegacySessionId(String id)
{
    public LegacySessionId
    {
        requireNonNull(id, "id is null");
    }
}
