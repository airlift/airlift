package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import static io.airlift.mcp.model.ResultType.TASK;
import static java.util.Objects.requireNonNull;

public record CreateTaskResult(@JsonUnwrapped Task task)
        implements Result
{
    public CreateTaskResult
    {
        requireNonNull(task, "task is null");
    }

    @JsonProperty
    public ResultType resultType()
    {
        return TASK;
    }
}
