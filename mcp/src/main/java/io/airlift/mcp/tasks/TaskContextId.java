package io.airlift.mcp.tasks;

import static java.util.Objects.requireNonNull;

public record TaskContextId(String id)
{
    public TaskContextId
    {
        requireNonNull(id, "id is null");
    }
}
