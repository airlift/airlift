package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record GetTaskRequest(String taskId, Optional<Map<String, Object>> meta)
        implements Meta
{
    public GetTaskRequest
    {
        requireNonNull(taskId, "taskId is null");
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public GetTaskRequest withMeta(Map<String, Object> meta)
    {
        return new GetTaskRequest(taskId, Optional.of(meta));
    }
}
