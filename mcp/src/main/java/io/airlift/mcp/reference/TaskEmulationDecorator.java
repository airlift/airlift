package io.airlift.mcp.reference;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.Content.TextContent;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.TaskMetadata;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.tasks.CombinedIds;
import io.airlift.mcp.tasks.TaskController;
import io.airlift.mcp.tasks.TaskId;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.airlift.mcp.tasks.CombinedIds.combineIds;
import static io.airlift.mcp.tasks.CombinedIds.splitIds;
import static java.util.Objects.requireNonNull;

public class TaskEmulationDecorator
{
    private static final Logger log = Logger.get(TaskEmulationDecorator.class);

    private static final int DEFAULT_POLL_INTERVAL_MS = 500;

    private final Optional<TaskController> maybeTaskController;

    @Inject
    public TaskEmulationDecorator(Optional<TaskController> maybeTaskController)
    {
        this.maybeTaskController = requireNonNull(maybeTaskController, "taskController is null");
    }

    public ToolHandler handleMcpResponse(ToolHandler delegate)
    {
        return maybeTaskController.map(controller -> internalDecorate(controller, delegate)).orElse(delegate);
    }

    public void handleMcpResponse(JSONRPCResponse rpcResponse, Runnable rejectionHandler)
    {
        maybeTaskController.ifPresentOrElse(taskController -> checkForTaskResult(taskController, rpcResponse), rejectionHandler);
    }

    private ToolHandler internalDecorate(TaskController taskController, ToolHandler delegate)
    {
        return (requestContext, toolRequest) -> {
            CallToolRequest decoratedToolRequest = toolRequest.withTaskMetadata(TaskMetadata.EMPTY);
            CallToolResult callToolResult = delegate.callTool(requestContext, decoratedToolRequest);
            return callToolResult.task().map(task -> taskEmulation(taskController, requestContext, new TaskId(task.taskId()), task.pollInterval().orElse(DEFAULT_POLL_INTERVAL_MS))).orElse(callToolResult);
        };
    }

    private void checkForTaskResult(TaskController taskController, JSONRPCResponse rpcResponse)
    {
        try {
            CombinedIds<TaskId, UUID> combinedIds = splitIds(String.valueOf(rpcResponse.id()), TaskId::new, UUID::fromString);
            TaskId taskId = combinedIds.a();
            UUID requestId = combinedIds.b();

            Optional<JsonRpcErrorDetail> error = Optional.ofNullable(rpcResponse.error()).map(rpcError -> new JsonRpcErrorDetail(rpcError.code(), Optional.ofNullable(rpcError.message()).orElse(""), Optional.ofNullable(rpcError.data())));
            taskController.setServerClientResponse(taskId, requestId, Optional.ofNullable(rpcResponse.result()), error);
        }
        catch (Exception _) {
            log.warn("Unable to parse request ID from response: %s", rpcResponse.id());
        }
    }

    private CallToolResult taskEmulation(TaskController taskController, McpRequestContext requestContext, TaskId taskId, int pollIntervalMs)
    {
        try {
            TaskStatus taskStatus = TaskStatus.WORKING;
            while ((taskStatus == TaskStatus.WORKING) || (taskStatus == TaskStatus.INPUT_REQUIRED)) {
                try {
                    TimeUnit.MILLISECONDS.sleep(pollIntervalMs);

                    taskStatus = taskController.task(taskId).map(task -> {
                        // TODO add ping periodically using requestContext

                        taskController.takeServerToClientMessages(taskId, message -> {
                            Object adjustedRequestId = addTaskIdToRequestId(message.id(), taskId);
                            requestContext.sendRequest(adjustedRequestId, message.method(), message.params());
                        });
                        return task.status();
                    }).orElse(TaskStatus.FAILED);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    taskStatus = TaskStatus.CANCELLED;
                }
                catch (Exception e) {
                    log.error(e, "Task emulation failed for task %s", taskId);
                    taskStatus = TaskStatus.FAILED;
                }
            }

            return switch (taskStatus) {
                case WORKING -> throw new IllegalStateException("Task is still working");
                case INPUT_REQUIRED -> throw new IllegalStateException("Task is still waiting on input");
                case FAILED, CANCELLED -> new CallToolResult(ImmutableList.of(new TextContent("Task failed")), Optional.empty(), true);
                case COMPLETED -> taskController.getTaskResult(taskId).orElseGet(() -> new CallToolResult(ImmutableList.of(new TextContent("No result returned")), Optional.empty(), false));
            };
        }
        finally {
            taskController.deleteTask(taskId);
        }
    }

    private static Object addTaskIdToRequestId(Object requestId, TaskId taskId)
    {
        if (requestId == null) {
            return null;
        }
        return combineIds(taskId.id(), requestId.toString());
    }
}
