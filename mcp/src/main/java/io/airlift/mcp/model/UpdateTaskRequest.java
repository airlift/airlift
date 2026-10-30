package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

/**
 * Params of {@code tasks/update}. The extension requires {@code inputResponses}, so an absent
 * value is kept apart from an empty one: a request without the field is malformed and is rejected,
 * while a request answering nothing is acknowledged.
 */
public record UpdateTaskRequest(String taskId, Optional<Map<String, Object>> inputResponses)
{
    public UpdateTaskRequest
    {
        requireNonNull(taskId, "taskId is null");
        inputResponses = requireNonNullElse(inputResponses, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
    }
}
