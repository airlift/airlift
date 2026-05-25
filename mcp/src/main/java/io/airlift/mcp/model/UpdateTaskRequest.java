package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static java.util.Objects.requireNonNull;

public record UpdateTaskRequest(String taskId, Map<String, Object> inputResponses)
{
    public UpdateTaskRequest
    {
        requireNonNull(taskId, "taskId is null");
        inputResponses = ImmutableMap.copyOf(inputResponses);
    }
}
