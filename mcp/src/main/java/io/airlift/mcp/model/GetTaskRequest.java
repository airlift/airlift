package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record GetTaskRequest(String taskId, Optional<Map<String, Object>> meta)
        implements Meta
{
    public GetTaskRequest
    {
        requireNonNull(taskId, "taskId is null");
        meta = firstNonNull(meta, Optional.empty());
    }

    @Override
    public GetTaskRequest withMeta(Map<String, Object> meta)
    {
        return new GetTaskRequest(taskId, Optional.of(meta));
    }
}
