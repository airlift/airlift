package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public record ResultTypeWrapper(ResultType resultType, @JsonUnwrapped Object result)
        implements ToolResult
{
    public ResultTypeWrapper
    {
        requireNonNull(resultType, "resultType is null");
        requireNonNull(result, "result is null");

        checkArgument(!(result instanceof ResultTypeWrapper), "result cannot be of type ResultTypeWrapper");
    }
}
