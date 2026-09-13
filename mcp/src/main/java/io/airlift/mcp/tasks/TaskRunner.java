package io.airlift.mcp.tasks;

import io.airlift.log.Logger;
import io.airlift.mcp.McpClientException;
import io.airlift.mcp.McpException;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.TaskResult;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;
import java.util.Set;

import static com.google.common.collect.Sets.newIdentityHashSet;
import static io.airlift.mcp.InvocationExceptions.mapInvocationException;
import static io.airlift.mcp.model.JsonRpcErrorCode.INTERNAL_ERROR;

public final class TaskRunner
{
    private static final Logger log = Logger.get(TaskRunner.class);

    private static final String NULL_RESULT_MESSAGE = "executor returned a null result";
    private static final String INPUT_REQUIRED_RESULT_MESSAGE = "A task executor must ask for input with TaskContext.requestInput or awaitInputResponses, not return an input-required result";

    private TaskRunner() {}

    /**
     * Engines run executors through this so that an executor settles the same way whoever runs it.
     * What the executor throws is reported as it would be on a plain tool call - see
     * {@link io.airlift.mcp.InvocationExceptions#mapInvocationException}: an error the tool would have returned in-band completes
     * the task with that error result, and a protocol error fails the task with its error. A
     * {@link CallToolResult} that asks for input - with {@code inputRequests} or {@code requestState} - fails
     * the task, as nothing would answer it once the task completed.
     */
    public static TaskResult run(TaskExecutor executor, McpIdentity.Authenticated<?> identity, CallToolRequest callToolRequest, TaskContext taskContext)
    {
        TaskResult result;
        try {
            result = executor.runTask(identity, callToolRequest, taskContext);
        }
        catch (InterruptedException e) {
            return interrupted(taskContext);
        }
        catch (Throwable e) {
            // an Error has to settle the task too - otherwise it reports working until its TTL expires
            return failed(e, taskContext);
        }

        // a null result would leave the task running forever
        if (result == null) {
            log.warn("Task %s executor returned a null result", taskContext.taskId());
            return new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, NULL_RESULT_MESSAGE));
        }
        // completing the task with an input-required result would bury requests nothing answers - a task asks through its TaskContext
        if ((result instanceof CallToolResult callToolResult) && (callToolResult.inputRequests().isPresent() || callToolResult.requestState().isPresent())) {
            return new TaskResult.Failed(new JsonRpcErrorDetail(INTERNAL_ERROR, INPUT_REQUIRED_RESULT_MESSAGE));
        }
        return result;
    }

    private static TaskResult failed(Throwable throwable, TaskContext taskContext)
    {
        // a tool may wrap the interrupt its cancellation caused - that must still settle as cancelled
        if (isInterruption(throwable)) {
            return interrupted(taskContext);
        }
        if (taskContext.isCancellationRequested()) {
            return TaskResult.Cancelled.INSTANCE;
        }

        // the generic message stands in for one that may say more than a client should see, as on the synchronous path
        RuntimeException mapped = mapInvocationException(throwable, "Failed to run task " + taskContext.taskId());
        if (mapped instanceof McpClientException clientException) {
            // the in-band error result the tool would have returned synchronously
            return CallToolResult.forError(clientException);
        }
        McpException mcpException = (McpException) mapped;
        // not self-contained only when made from a checked exception, an Error or a Jackson exception - a task fails on a
        // worker thread with no HTTP error to show for it, so this is its only trace for operators
        if (!mcpException.isSelfContained()) {
            log.warn(throwable, "Task %s failed", taskContext.taskId());
        }
        return new TaskResult.Failed(mcpException.errorDetail());
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
            // a socket timeout is an InterruptedIOException too, but nothing interrupted the thread
            boolean interruptedIo = (cause instanceof InterruptedIOException) && !(cause instanceof SocketTimeoutException);
            if ((cause instanceof InterruptedException) || interruptedIo || (cause instanceof ClosedByInterruptException)) {
                return true;
            }
        }
        return false;
    }
}
