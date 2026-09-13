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
 * the task as it is; whatever it throws is turned into a result for it by {@link TaskRunner#run}.
 */
@FunctionalInterface
public interface TaskExecutor
{
    TaskResult runTask(Authenticated<?> identity, CallToolRequest callToolRequest, TaskContext taskContext)
            throws Exception;
}
