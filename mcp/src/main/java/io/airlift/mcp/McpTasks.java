package io.airlift.mcp;

import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.tasks.TaskAdapter;
import io.airlift.mcp.tasks.TaskContextId;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public interface McpTasks
{
    static JsonRpcMessage buildResult(TaskAdapter task, CallToolResult callToolResult)
    {
        return new JsonRpcResponse<>(task.requestId(), Optional.empty(), Optional.of(callToolResult));
    }

    static JsonRpcMessage buildError(TaskAdapter task, JsonRpcErrorDetail errorDetail)
    {
        return new JsonRpcResponse<>(task.requestId(), Optional.of(errorDetail), Optional.empty());
    }

    TaskContextId newTaskContextId(Optional<McpIdentity> identity);

    void deleteTaskContext(TaskContextId taskContextId);

    interface TaskBuilder
    {
        TaskBuilder withRequestId(Object requestId);

        TaskBuilder addAttribute(String key, String value);

        TaskBuilder withProgressToken(Object progressToken);

        TaskBuilder withPollInterval(int pollIntervalMs);

        TaskBuilder withTtlInterval(int ttlMs);

        TaskAdapter create();
    }

    TaskBuilder createTask(TaskContextId taskContextId);

    Optional<TaskAdapter> getTask(TaskContextId taskContextId, String taskId);

    List<TaskAdapter> listTasks(TaskContextId taskContextId, int pageSize, Optional<String> taskIdCursor);

    boolean deleteTask(TaskContextId taskContextId, String taskId);

    boolean setTaskAttributes(TaskContextId taskContextId, String taskId, Map<String, String> attributes);

    boolean setTaskMessage(TaskContextId taskContextId, String taskId, JsonRpcMessage message, Optional<String> statusMessage);

    boolean requestTaskCancellation(TaskContextId taskContextId, String taskId, Optional<String> statusMessage);

    <R> JsonRpcResponse<R> serverToClientRequest(TaskContextId taskContextId, String taskId, String method, Object params, Class<R> responseType, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;
}
