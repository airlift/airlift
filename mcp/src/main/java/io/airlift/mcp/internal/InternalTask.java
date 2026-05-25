package io.airlift.mcp.internal;

import io.airlift.mcp.McpTaskController.ErrorState;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

// public for Jackson
public record InternalTask(
        ErrorState errorState,
        Instant createdAt,
        Instant updatedAt,
        Optional<String> statusMessage,
        Optional<Map<String, Object>> inputResponses,
        Optional<CallToolResult> result,
        Optional<JsonRpcErrorDetail> error,
        Optional<InternalTaskExecution> execution)
{
    public InternalTask
    {
        requireNonNull(errorState, "errorState is null");
        requireNonNull(createdAt, "createdAt is null");
        requireNonNull(updatedAt, "updatedAt is null");
        requireNonNull(statusMessage, "statusMessage is null");
        requireNonNull(inputResponses, "inputResponses is null");
        requireNonNull(result, "result is null");
        requireNonNull(error, "error is null");
        requireNonNull(execution, "execution is null");
    }

    InternalTask withErrorState(ErrorState errorState, Optional<JsonRpcErrorDetail> errorDetail)
    {
        return new InternalTask(errorState, createdAt, Instant.now(), errorDetail.map(JsonRpcErrorDetail::message), inputResponses, result, errorDetail, execution);
    }

    InternalTask withResult(Optional<CallToolResult> result, Optional<String> statusMessage)
    {
        return new InternalTask(errorState, createdAt, Instant.now(), statusMessage, Optional.empty(), result, error, execution);
    }

    InternalTask withInputResponses(Optional<Map<String, Object>> inputResponses)
    {
        return new InternalTask(errorState, createdAt, Instant.now(), statusMessage, inputResponses, result, error, execution);
    }

    InternalTask withExecution(InternalTaskExecution execution)
    {
        return new InternalTask(errorState, createdAt, Instant.now(), statusMessage, inputResponses, result, error, Optional.of(execution));
    }
}
