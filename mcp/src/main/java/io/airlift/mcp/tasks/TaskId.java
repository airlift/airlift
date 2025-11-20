package io.airlift.mcp.tasks;

import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Objects.requireNonNull;

public record TaskId(@JsonValue String id)
{
    public TaskId
    {
        requireNonNull(id, "id is null");
    }
}
