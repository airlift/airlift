package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.Meta.normalize;
import static java.util.Objects.requireNonNull;

public record TaskNotification(@JsonUnwrapped Task task, Optional<Map<String, Object>> meta)
        implements Meta<TaskNotification>
{
    public TaskNotification
    {
        requireNonNull(task, "task is null");
        meta = normalize(meta);
    }

    @Override
    public TaskNotification withMeta(Map<String, Object> meta)
    {
        return new TaskNotification(task, Optional.of(meta));
    }
}
