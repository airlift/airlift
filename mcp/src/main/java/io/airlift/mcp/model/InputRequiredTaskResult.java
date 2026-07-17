package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.model.ResultType.COMPLETE;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record InputRequiredTaskResult(@JsonUnwrapped Task task, Optional<Map<String, InputRequest>> inputRequests)
        implements Result
{
    public InputRequiredTaskResult
    {
        requireNonNull(task, "task is null");
        inputRequests = requireNonNullElse(inputRequests, Optional.empty());
    }

    @JsonProperty
    public ResultType resultType()
    {
        return COMPLETE;
    }
}
