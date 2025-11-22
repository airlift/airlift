package io.airlift.mcp.sessions;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Objects.requireNonNull;

public record SessionId(@JsonValue String id)
{
    public SessionId
    {
        requireNonNull(id, "id is null");
    }
}
