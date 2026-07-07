package io.airlift.mcp.model;

import io.airlift.mcp.McpTaskController.ErrorState;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public sealed interface TaskHandlerResult
        permits CallToolResult, TaskHandlerResult.TaskFailed
{
    record TaskFailed(ErrorState errorState, Optional<JsonRpcErrorDetail> errorDetail)
            implements TaskHandlerResult
    {
        public TaskFailed
        {
            requireNonNull(errorState, "errorState is null");
            requireNonNull(errorDetail, "errorDetail is null");
        }
    }
}
