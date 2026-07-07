package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

public record GetTaskRequest(String taskId)
{
    public GetTaskRequest
    {
        requireNonNull(taskId, "taskId is null");
    }
}
