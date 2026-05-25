package io.airlift.mcp.model;

import io.airlift.mcp.McpTaskController.ErrorState;

import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public sealed interface TaskHandlerResult
        permits CallToolResult,
                TaskHandlerResult.Incomplete,
                TaskHandlerResult.TaskFailed
{
    record TaskFailed(ErrorState errorState, Optional<JsonRpcErrorDetail> errorDetail)
            implements TaskHandlerResult
    {
        public TaskFailed
        {
            requireNonNull(errorState, "errorState is null");
            errorDetail = requireNonNullElse(errorDetail, Optional.empty());
        }
    }

    record Incomplete()
            implements TaskHandlerResult
    {
        public static final TaskHandlerResult INSTANCE = new Incomplete();
    }
}
