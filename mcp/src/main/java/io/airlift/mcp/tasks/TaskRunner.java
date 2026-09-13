package io.airlift.mcp.tasks;

import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.TaskResult;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Set;

import static com.google.common.collect.Sets.newIdentityHashSet;
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
            return interrupted(taskContext);
        }
        catch (McpClientException e) {
            // the in-band error result the tool would have returned synchronously
            return CallToolResult.forError(e);
        }
        catch (McpException e) {
            return new TaskResult.Failed(e.errorDetail());
        }
        catch (Throwable e) {
            // an Error has to settle the task too - otherwise it reports working until its TTL expires
            if (isInterruption(e)) {
                return interrupted(taskContext);
            }
            if (taskContext.isCancellationRequested()) {
                return TaskResult.Cancelled.INSTANCE;
            }
            return new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, requireNonNullElse(e.getMessage(), "Internal error")));
        }
    }

    private static TaskResult interrupted(TaskContext taskContext)
    {
        // the interrupt belongs to whoever owns the thread - catching it must not hide it from them
        Thread.currentThread().interrupt();
        if (taskContext.isCancellationRequested()) {
            return TaskResult.Cancelled.INSTANCE;
        }
        return new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, "Task execution was interrupted"));
    }

    // an interrupt can reach the executor wrapped in another exception, or as interrupted I/O
    private static boolean isInterruption(Throwable throwable)
    {
        Set<Throwable> seen = newIdentityHashSet();
        for (Throwable cause = throwable; (cause != null) && seen.add(cause); cause = cause.getCause()) {
            if ((cause instanceof InterruptedException) || (cause instanceof InterruptedIOException) || (cause instanceof ClosedByInterruptException)) {
                return true;
            }
        }
        return false;
    }
}
