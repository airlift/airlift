package io.airlift.mcp.tasks;

import io.airlift.mcp.model.CallToolResult;

import java.time.Instant;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record TaskResult(CallToolResult callToolResult, EndTaskReason endTaskReason, Optional<String> statusMessage, Instant completionTime)
{
    public TaskResult
    {
        requireNonNull(callToolResult, "callToolResult is null");
        requireNonNull(endTaskReason, "endTaskReason is null");
        requireNonNull(statusMessage, "statusMessage is null");
        requireNonNull(completionTime, "completionTime is null");
    }
}
