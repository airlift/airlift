package io.airlift.mcp.operations;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.airlift.mcp.model.ResultType;

import static java.util.Objects.requireNonNull;

public record ResultTypeWrapper(ResultType resultType, @JsonUnwrapped Object result)
{
    public ResultTypeWrapper
    {
        requireNonNull(resultType, "resultType is null");
        requireNonNull(result, "result is null");
    }
}
