package io.airlift.mcp.reference;

import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.handler.ToolHandler;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.tasks.CombinedIds;
import io.airlift.mcp.tasks.EndTaskReason;
import io.airlift.mcp.tasks.TaskController;
import io.airlift.mcp.tasks.TaskId;
import io.airlift.mcp.tasks.TaskResult;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.airlift.mcp.tasks.CombinedIds.combineIds;
import static io.airlift.mcp.tasks.CombinedIds.splitIds;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

public class TaskEmulationDecorator
{
    private static final Logger log = Logger.get(TaskEmulationDecorator.class);

    private final Optional<TaskController> maybeTaskController;
    private final Duration handlerTimeout;
    private final int defaultPollingIntervalMs;
    private final OptionalInt taskTtlMs;

    @Inject
    public TaskEmulationDecorator(Optional<TaskController> maybeTaskController, TaskEmulationConfig config)
    {
        this.maybeTaskController = requireNonNull(maybeTaskController, "taskController is null");

        handlerTimeout = config.getEmulationHandlerTimeout().toJavaTime();
        defaultPollingIntervalMs = toIntExact(config.getDefaultPollingInterval().toMillis());
        taskTtlMs = OptionalInt.of(toIntExact(config.getTaskTtl().toMillis()));
    }

    public ToolHandler decorateTool(ToolHandler delegate)
    {
        return maybeTaskController.map(controller -> internalDecorate(controller, delegate)).orElse(delegate);
    }

    public void handleMcpResponse(JSONRPCResponse rpcResponse, Runnable rejectionHandler)
    {
        maybeTaskController.ifPresentOrElse(taskController -> checkForTaskResponse(taskController, rpcResponse), rejectionHandler);
    }

    private ToolHandler internalDecorate(TaskController taskController, ToolHandler delegate)
    {
        return (requestContext, toolRequest) -> {
            CallToolRequest decoratedToolRequest = toolRequest.withTaskMetadata(() -> taskTtlMs);
            CallToolResult callToolResult = delegate.callTool(requestContext, decoratedToolRequest);
            return callToolResult.task().map(task -> taskEmulation(taskController, requestContext, new TaskId(task.taskId()), task.pollInterval().orElse(defaultPollingIntervalMs)))
                    .orElse(callToolResult);
        };
    }

    private void checkForTaskResponse(TaskController taskController, JSONRPCResponse rpcResponse)
    {
        try {
            CombinedIds<TaskId, UUID> combinedIds = splitIds(String.valueOf(rpcResponse.id()), TaskId::new, UUID::fromString);
            TaskId taskId = combinedIds.a();
            UUID requestId = combinedIds.b();

            log.debug("checkForTaskResult received for task %s, request %s", taskId, requestId);

            Optional<JsonRpcErrorDetail> error = Optional.ofNullable(rpcResponse.error()).map(rpcError -> new JsonRpcErrorDetail(rpcError.code(), Optional.ofNullable(rpcError.message())
                    .orElse(""), Optional.ofNullable(rpcError.data())));
            if (!taskController.setServerClientResponse(taskId, requestId, Optional.ofNullable(rpcResponse.result()), error)) {
                log.warn("Unable to set response for task %s, request %s: invalid task ID", taskId, requestId);
            }
        }
        catch (Exception _) {
            log.warn("Unable to parse request ID from response: %s", rpcResponse.id());
        }
    }

    private CallToolResult taskEmulation(TaskController taskController, McpRequestContext requestContext, TaskId taskId, int pollIntervalMs)
    {
        log.debug("Task emulation started for %s", taskId);

        Stopwatch stopwatch = Stopwatch.createStarted();

        CallToolResult callToolResult = null;
        while (callToolResult == null) {
            Optional<TaskResult> maybeTaskResult = taskController.finalizeTask(taskId);
            if (maybeTaskResult.isPresent()) {
                callToolResult = maybeTaskResult.get().callToolResult();
                continue;
            }

            try {
                if (stopwatch.elapsed().compareTo(handlerTimeout) > 0) {
                    log.warn("Task emulation timed out for %s", taskId);
                    taskController.endTask(taskId, CallToolResult.error("Task timed out"), EndTaskReason.FAILED, Optional.empty());
                }
                else {
                    TimeUnit.MILLISECONDS.sleep(pollIntervalMs);

                    // TODO add ping periodically using requestContext

                    taskController.takeServerToClientMessages(taskId, message -> {
                        Object adjustedRequestId = addTaskIdToRequestId(message.id(), taskId);
                        requestContext.sendRequest(adjustedRequestId, message.method(), message.params());
                    });
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                try {
                    taskController.endTask(taskId, CallToolResult.error("Task interrupted"), EndTaskReason.CANCELLED, Optional.empty());
                }
                catch (Exception setStatusException) {
                    log.error("Unable to set task status for task %s", taskId, setStatusException);
                    throw setStatusException;
                }
            }
            catch (Exception e) {
                log.error(e, "Task emulation failed for task %s", taskId);
                try {
                    taskController.endTask(taskId, CallToolResult.error("Task emulation failed"), EndTaskReason.FAILED, Optional.empty());
                }
                catch (Exception setStatusException) {
                    log.error("Unable to set task status for task %s", taskId, setStatusException);
                    throw setStatusException;
                }
            }
        }

        log.debug("Task emulation finished for %s", taskId);
        return callToolResult;
    }

    private static Object addTaskIdToRequestId(Object requestId, TaskId taskId)
    {
        if (requestId == null) {
            return null;
        }
        return combineIds(taskId.id(), requestId.toString());
    }
}
