package io.airlift.mcp.model;

import static java.util.Objects.requireNonNull;

/**
 * How a {@code TaskExecutor} settled. A {@link CallToolResult} completes the
 * task - including one with {@code isError} set, as the Tasks extension reserves {@code failed}
 * for JSON-RPC errors. Engines record a terminal result as the task's state and leave a
 * {@link Suspended} task as it is.
 */
public sealed interface TaskResult
        permits CallToolResult,
                TaskResult.Cancelled,
                TaskResult.Failed,
                TaskResult.Suspended
{
    record Failed(JsonRpcErrorDetail error)
            implements TaskResult
    {
        public Failed
        {
            requireNonNull(error, "error is null");
        }
    }

    record Cancelled()
            implements TaskResult
    {
        public static final TaskResult INSTANCE = new Cancelled();
    }

    /**
     * The executor has not finished - it is waiting for input it asked for with
     * {@code TaskContext.requestInput}. The task keeps the status the
     * executor left it in. With no input pending nothing would run the task again, so
     * {@code MemoryTaskEngine} fails a task suspended that way.
     */
    record Suspended()
            implements TaskResult
    {
        public static final TaskResult INSTANCE = new Suspended();
    }
}
