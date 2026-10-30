package io.airlift.mcp.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS;
import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public record Task(
        String taskId,
        TaskStatus status,
        Optional<String> statusMessage,
        String createdAt,
        String lastUpdatedAt,
        @JsonInclude(ALWAYS) OptionalLong ttlMs,
        OptionalLong pollIntervalMs,
        Optional<Map<String, InputRequest>> inputRequests,
        Optional<CallToolResult> result,
        Optional<JsonRpcErrorDetail> error)
        implements ToolResult
{
    public Task
    {
        requireNonNull(taskId, "taskId is null");
        requireNonNull(status, "status is null");
        statusMessage = requireNonNullElse(statusMessage, Optional.empty());
        requireNonNull(createdAt, "createdAt is null");
        requireNonNull(lastUpdatedAt, "lastUpdatedAt is null");
        ttlMs = requireNonNullElse(ttlMs, OptionalLong.empty());
        pollIntervalMs = requireNonNullElse(pollIntervalMs, OptionalLong.empty());
        inputRequests = requireNonNullElse(inputRequests, Optional.<Map<String, InputRequest>>empty()).map(ImmutableMap::copyOf);
        result = requireNonNullElse(result, Optional.empty());
        error = requireNonNullElse(error, Optional.empty());

        switch (status) {
            case WORKING, CANCELLED -> {
                checkArgument(inputRequests.isEmpty(), "%s tasks cannot have inputRequests", status);
                checkArgument(result.isEmpty(), "%s tasks cannot have a result", status);
                checkArgument(error.isEmpty(), "%s tasks cannot have an error", status);
            }

            case INPUT_REQUIRED -> {
                checkArgument(inputRequests.map(requests -> !requests.isEmpty()).orElse(false), "%s tasks must have inputRequests", status);
                checkArgument(result.isEmpty(), "%s tasks cannot have a result", status);
                checkArgument(error.isEmpty(), "%s tasks cannot have an error", status);
            }

            case COMPLETED -> {
                checkArgument(inputRequests.isEmpty(), "%s tasks cannot have inputRequests", status);
                checkArgument(result.isPresent(), "%s tasks must have a result", status);
                checkArgument(error.isEmpty(), "%s tasks cannot have an error", status);
            }

            case FAILED -> {
                checkArgument(inputRequests.isEmpty(), "%s tasks cannot have inputRequests", status);
                checkArgument(result.isEmpty(), "%s tasks cannot have a result", status);
                checkArgument(error.isPresent(), "%s tasks must have an error", status);
            }
        }
    }
}
