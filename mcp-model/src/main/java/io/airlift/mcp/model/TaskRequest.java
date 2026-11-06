package io.airlift.mcp.model;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;

public record TaskRequest(String taskId, Optional<Map<String, Object>> meta)
        implements Meta<TaskRequest>
{
    public TaskRequest
    {
        requireNonNull(taskId, "taskId is null");
        meta = normalize(meta);
    }

    public TaskRequest(String taskId)
    {
        this(taskId, Optional.empty());
    }

    @Override
    public TaskRequest withMeta(Map<String, Object> meta)
    {
        return new TaskRequest(taskId, Optional.of(meta));
    }
}
