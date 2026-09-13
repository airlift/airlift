package io.airlift.mcp.tasks;

import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.McpTasks;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.TaskResult;

/**
 * The work a task performs. {@link McpTasks#executeTask} runs the executor it is given; an engine
 * whose tasks run in another process builds its own executor there from the same tool
 * registrations.
 * <p>
 * It runs outside of the request that created the task and must be prepared to be interrupted when
 * the task is cancelled. It reports how the task settled, or {@link TaskResult.Suspended} to leave
 * the task as it is. What it throws settles the task the way a plain tool call reports it, by
 * {@link TaskRunner#run}: an exception the tool call would answer with an error result - an
 * unchecked exception, with its own message, or an {@link io.airlift.mcp.McpException} that is
 * not self-contained - completes the task with that error result, and one it would answer with a
 * protocol error fails the task. A checked exception or an {@link Error} fails it with a generic
 * message, and is logged as a warning.
 * It asks for input through its {@link TaskContext}: a {@link io.airlift.mcp.model.CallToolResult}
 * with {@code inputRequests} or {@code requestState}, as a synchronous tool call returns, fails the
 * task.
 */
@FunctionalInterface
public interface TaskExecutor
{
    TaskResult runTask(Authenticated<?> identity, CallToolRequest callToolRequest, TaskContext taskContext)
            throws Exception;
}
