package io.airlift.mcp;

import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.InputResponses;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskErrorState;
import io.airlift.mcp.model.TaskHandler;
import io.airlift.mcp.model.ToolResult;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface McpTaskController
{
    boolean executeCancelable(String taskId, TaskHandler handler);

    Optional<Task> getTask(String taskId);

    Optional<ToolResult> currentTaskResult(String taskId);

    InputResponses currentInputResponses(String taskId);

    TaskErrorState getErrorState(String taskId);

    SetStatus setErrorState(String taskId, TaskErrorState errorState, Optional<JsonRpcErrorDetail> errorDetail);

    SetStatus setTaskInputResponses(String taskId, Optional<Map<String, Object>> inputResponses);

    SetStatus setResult(String taskId, Optional<CallToolResult> result, Optional<String> statusMessage);

    boolean awaitInputResponses(String taskId, Duration timeout, Set<String> keys)
            throws InterruptedException;

    boolean await(String taskId, Duration timeout)
            throws InterruptedException;

    enum SetStatus
    {
        SUCCESS,
        TASK_NOT_FOUND,
        TASK_COMPLETED,
    }
}
