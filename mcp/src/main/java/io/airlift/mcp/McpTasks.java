package io.airlift.mcp;

import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskResult;
import io.airlift.mcp.tasks.TaskContext;
import io.airlift.mcp.tasks.TaskExecutor;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Owns the state of tasks. Everything the {@code tasks/*} methods and the task-executing tools
 * need goes through this interface, so a server can keep tasks in memory - see
 * {@link io.airlift.mcp.tasks.memory.MemoryTaskEngine} - or hand them to a database, a worker
 * pool or a durable execution runtime without any tool changing.
 * <p>
 * Running a task's tool call is a separate concern: {@link #executeTask} is the only method that
 * runs anything, and an engine whose own workers run tasks implements it as a no-op.
 * <p>
 * Only {@link #validateTaskAccess} checks who is asking. Callers serving a client request must
 * use it before every other operation on a task.
 * <p>
 * Implementations must:
 * <ul>
 *     <li>generate task ids that cannot be guessed by a third party</li>
 *     <li>not return from {@link #createTask} until a {@link #getTask} for the new task would
 *     succeed - clients are entitled to poll immediately</li>
 *     <li>bind a task to the identity that created it and refuse any other identity,
 *     indistinguishably from an unknown task, so that a caller cannot discover another
 *     caller's tasks. Identities are compared with {@code equals()}</li>
 *     <li>discard a task once its TTL has elapsed</li>
 * </ul>
 */
public interface McpTasks
{
    /**
     * Durably creates a task in the {@code working} status. It records the tool call but does not
     * run it. The returned task is the handle sent to the client.
     */
    Task createTask(McpRequestContext requestContext, CallToolRequest callToolRequest, Duration ttl, Duration pollInterval);

    /**
     * Runs the task's tool call, or schedules it to be run. Whatever runs it hands the tool call
     * and the task's {@link TaskContext} to {@code executor} and records the resulting
     * {@link TaskResult} against the task. Running it inline blocks the request that created the
     * task, which suits a task that settles immediately.
     * <p>
     * Driving a task to a terminal state is the caller's responsibility, and it need not happen
     * here: an engine whose own workers run tasks implements this as a no-op, and they pick the
     * task up from its store with an executor built there from the same tool registrations. Until
     * something runs it the task stays {@code working}, and it expires with its TTL.
     */
    void executeTask(String taskId, TaskExecutor executor);

    /**
     * Whether the task exists and belongs to the caller. Unknown, expired and other callers'
     * tasks are all {@code false} - a caller cannot tell them apart.
     */
    boolean validateTaskAccess(McpRequestContext requestContext, String taskId);

    /**
     * The complete task - including the payload for its status - or empty if it is unknown or
     * expired.
     */
    Optional<Task> getTask(String taskId);

    /**
     * Delivers responses to input requests the task surfaced. Unknown and already-answered keys
     * are ignored. Returns {@code false} if the task is unknown or expired.
     */
    boolean updateTask(String taskId, Map<String, Object> inputResponses);

    /**
     * Cancellation is cooperative: the task may still settle as {@code completed} or
     * {@code failed} if it finishes first, and cancelling a terminal task does nothing. Returns
     * {@code false} if the task is unknown or expired.
     */
    boolean cancelTask(String taskId);
}
