package io.airlift.mcp.model;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public sealed interface TaskHandlerResult
        permits CallToolResult, TaskHandlerResult.TaskFailed
{
    record TaskFailed(TaskErrorState errorState, Optional<JsonRpcErrorDetail> errorDetail)
            implements TaskHandlerResult
    {
        public TaskFailed
        {
            requireNonNull(errorState, "errorState is null");
            errorDetail = requireNonNullElse(errorDetail, Optional.empty());
        }
    }
}
