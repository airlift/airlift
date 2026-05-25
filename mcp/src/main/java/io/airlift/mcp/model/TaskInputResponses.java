package io.airlift.mcp.model;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public final class TaskInputResponses
        implements InputResponses<TaskInputResponses>
{
    private final Optional<String> requestState;
    private final Optional<Map<String, Object>> inputResponses;

    public TaskInputResponses(Optional<String> requestState, Optional<Map<String, Object>> inputResponses)
    {
        this.requestState = requireNonNull(requestState, "requestState is null");
        this.inputResponses = requireNonNull(inputResponses, "inputResponses is null").map(ImmutableMap::copyOf);
    }

    @Override
    public Optional<String> requestState()
    {
        return requestState;
    }

    @Override
    public Optional<Map<String, Object>> inputResponses()
    {
        return inputResponses;
    }

    @Override
    public TaskInputResponses withInputResponses(Optional<String> requestState, Map<String, Object> inputResponses)
    {
        return new TaskInputResponses(requestState, Optional.of(inputResponses));
    }
}
