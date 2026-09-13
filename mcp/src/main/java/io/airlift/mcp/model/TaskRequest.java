package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record TaskRequest(String taskId)
{
    public TaskRequest
    {
        requireNonNull(taskId, "taskId is null");
    }
}
