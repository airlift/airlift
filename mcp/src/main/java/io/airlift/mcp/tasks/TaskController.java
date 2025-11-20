package io.airlift.mcp.tasks;

import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskStatus;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface TaskController
{
    /**
     * Create a new task and return its ID. It is assumed there is a separate, application-specific
     * processing system that is polling for new tasks and executing them, etc.
     */
    Task createTask(McpRequestContext requestContext, CallToolRequest callToolRequest);

    /**
     * @return the status for the given task ID, or {@link Optional#empty()} if the task ID is invalid
     */
    Optional<TaskStatus> taskStatus(TaskId taskId);

    /**
     * Set the result of the given task ID
     *
     * @return {@code true} if the task was updated, {@code false} if the task ID is invalid
     */
    boolean endTask(TaskId taskId, CallToolResult result, EndTaskReason reason, Optional<String> statusMessage);

    /**
     * @return the result of the given task ID, or {@link Optional#empty()} if the task ID is invalid or the task does not have a result
     */
    Optional<TaskResult> getTaskResult(TaskId taskId);

    /**
     * Same as {@link #getTaskResult(TaskId)} however, the task is removed/deleted after returning any result.
     *
     * @return the result of the given task ID, or {@link Optional#empty()} if the task ID is invalid or the task does not have a result
     */
    Optional<TaskResult> finalizeTask(TaskId taskId);

    /**
     * Queue a server-to-client message for the given task ID. If {@code requestId} is present,
     * the message is a request that expects a response; otherwise, it is a notification. It is an error
     * to queue a message with a duplicate {@code requestId}.
     *
     * @return {@code true} if the message was queued, {@code false} if the task ID is invalid
     */
    <T> boolean queueServerToClientMessage(TaskId taskId, Optional<UUID> requestId, String method, Optional<T> params);

    /**
     * Consume available queued server-to-client messages for the given task ID. This operation is intended to
     * be atomic if possible. i.e. each message should be consumed exactly once and will be removed from the queue
     * if the {@code messageConsumer} returns normally.
     */
    void takeServerToClientMessages(TaskId taskId, Consumer<JsonRpcRequest<?>> messageConsumer);

    /**
     * Set the response for a server-to-client request message that was previously queued.
     *
     * @return {@code true} if the response was set, {@code false} if the task ID or request ID is invalid
     */
    <T> boolean setServerClientResponse(TaskId taskId, UUID requestId, Optional<T> result, Optional<JsonRpcErrorDetail> error);

    /**
     * Consume the response for a server-to-client request message that was previously queued. This operation is intended to
     * be atomic if possible. i.e. a response should be consumed exactly once and will be removed from the task
     * if the {@code responseConsumer} returns normally.
     *
     * @return {@code true} if the response was consumed, {@code false} if the task ID or request ID is invalid
     */
    boolean takeServerToClientResponse(TaskId taskId, UUID requestId, Consumer<JsonRpcResponse<?>> responseConsumer);
}
