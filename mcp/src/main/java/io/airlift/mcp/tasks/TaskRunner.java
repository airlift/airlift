package io.airlift.mcp.tasks;

import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.TaskResult;

import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

public final class TaskRunner
{
    private TaskRunner() {}

    /**
     * Engines run executors through this so that an executor settles the same way whoever runs it.
     */
    public static TaskResult run(TaskExecutor executor, McpIdentity.Authenticated<?> identity, CallToolRequest callToolRequest, TaskContext taskContext)
    {
        try {
            // a null result would leave the task running forever - the catch below settles it as failed
            return requireNonNull(executor.runTask(identity, callToolRequest, taskContext), "executor returned a null result");
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (taskContext.isCancellationRequested()) {
                return TaskResult.Cancelled.INSTANCE;
            }
            return new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, "Task execution was interrupted"));
        }
        catch (McpClientException e) {
            // the in-band error result the tool would have returned synchronously
            return CallToolResult.forError(e);
        }
        catch (McpException e) {
            return new TaskResult.Failed(e.errorDetail());
        }
        catch (Exception e) {
            if (taskContext.isCancellationRequested()) {
                return TaskResult.Cancelled.INSTANCE;
            }
            return new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, requireNonNullElse(e.getMessage(), "Internal error")));
        }
    }
}
