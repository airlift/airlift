package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record UpdateTaskRequest(String taskId, Map<String, Object> inputResponses, Optional<Map<String, Object>> meta)
        implements Meta<UpdateTaskRequest>
{
    public UpdateTaskRequest
    {
        requireNonNull(taskId, "taskId is null");
        inputResponses = ImmutableMap.copyOf(inputResponses);
        meta = requireNonNullElse(meta, Optional.empty());
    }

    @Override
    public UpdateTaskRequest withMeta(Map<String, Object> meta)
    {
        return new UpdateTaskRequest(taskId, inputResponses, Optional.of(meta));
    }
}
