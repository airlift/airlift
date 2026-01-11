package io.airlift.mcp.tasks;

import com.google.common.base.Supplier;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcResponse;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public interface TaskController
{
    TaskAdapter createTask(NewTask newTask);

    void deleteTask(TaskContextId taskContextId, String taskId);

    Optional<TaskAdapter> getTask(TaskContextId taskContextId, String taskId);

    List<TaskAdapter> listTasks(TaskContextId taskContextId, int pageSize, Optional<String> lastTaskId);

    TaskAdapter blockUntilResponse(TaskContextId taskContextId, String taskId, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;

    TaskAdapter blockUntilCompleted(TaskContextId taskContextId, String taskId, Duration timeout, Duration pollInterval)
            throws InterruptedException, TimeoutException;

    void setTaskMessage(TaskContextId taskContextId, String taskId, JsonRpcMessage message, Optional<String> statusMessage);

    void requestTaskCancellation(TaskContextId taskContextId, String taskId, Optional<String> statusMessage);

    void completeTask(TaskContextId taskContextId, String taskId, JsonRpcResponse<?> response, Optional<String> statusMessage);

    void addServerToClientResponse(TaskContextId taskContextId, String taskId, JsonRpcResponse<?> response);

    <T> T executeCancellable(TaskContextId taskContextId, String taskId, Supplier<T> supplier);
}
