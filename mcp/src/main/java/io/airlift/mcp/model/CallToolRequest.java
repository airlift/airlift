package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Objects.requireNonNull;

public record CallToolRequest(String name, Map<String, Object> arguments, Optional<Map<String, Object>> meta, Optional<TaskMetadata> task)
        implements Meta
{
    public CallToolRequest
    {
        requireNonNull(name, "name is null");
        arguments = ImmutableMap.copyOf(arguments);
        meta = firstNonNull(meta, Optional.empty());
        requireNonNull(task, "task is null");
    }

    public CallToolRequest(String name, Map<String, Object> arguments)
    {
        this(name, arguments, Optional.empty(), Optional.empty());
    }

    public CallToolRequest withTaskMetadata(TaskMetadata taskMetadata)
    {
        return new CallToolRequest(name, arguments, meta, Optional.of(taskMetadata));
    }
}
