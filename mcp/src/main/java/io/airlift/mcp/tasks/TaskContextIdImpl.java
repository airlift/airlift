package io.airlift.mcp.tasks;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Objects.requireNonNull;

public record TaskContextIdImpl(@JsonValue String id)
        implements TaskContextId
{
    public TaskContextIdImpl
    {
        requireNonNull(id, "id is null");
    }
}
