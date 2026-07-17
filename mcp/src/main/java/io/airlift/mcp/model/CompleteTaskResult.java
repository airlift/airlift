package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.util.Optional;

import static io.airlift.mcp.model.ResultType.COMPLETE;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record CompleteTaskResult(@JsonUnwrapped Task task, Optional<CallToolResult> result)
        implements Result
{
    public CompleteTaskResult
    {
        requireNonNull(task, "task is null");
        result = requireNonNullElse(result, Optional.empty());
    }

    @JsonProperty
    public ResultType resultType()
    {
        return COMPLETE;
    }
}
