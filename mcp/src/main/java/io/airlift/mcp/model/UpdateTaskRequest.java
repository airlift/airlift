package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record UpdateTaskRequest(String taskId, Optional<Map<String, Object>> inputResponses)
{
    public UpdateTaskRequest
    {
        requireNonNull(taskId, "taskId is null");
        inputResponses = requireNonNullElse(inputResponses, Optional.<Map<String, Object>>empty()).map(ImmutableMap::copyOf);
    }

    @JsonIgnore
    public Map<String, Object> inputResponsesMap()
    {
        return inputResponses.orElseGet(ImmutableMap::of);
    }
}
