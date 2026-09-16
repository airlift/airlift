package io.airlift.mcp.tasks;

import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.TaskResult;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * The task a {@link TaskExecutor} is running: how it reports on the task and asks the client for
 * input.
 */
public interface TaskContext
{
    String taskId();

    void setStatusMessage(String statusMessage);

    /**
     * Tools that do not block should poll this and return promptly - blocking tools are
     * interrupted instead.
     */
    boolean isCancellationRequested();

    /**
     * Moves the task to {@code input_required} and returns: the requests are surfaced on
     * {@code tasks/get} and answered by {@code tasks/update}. An executor that does not want to
     * block asks for input, returns {@link TaskResult.Suspended}, and is run again once every
     * request has been answered - {@link #inputResponses} then has the answers.
     */
    void requestInput(Map<String, InputRequest> inputRequests);

    /**
     * The answers delivered so far to the requests made with {@link #requestInput}, keyed as they
     * were.
     */
    Map<String, Object> inputResponses();

    /**
     * Asks for input with {@link #requestInput} and blocks until {@code tasks/update} has answered
     * every key or {@code timeout} elapses. On timeout the unanswered requests are discarded and
     * only the answers received so far are returned. The task is {@code working} again on return.
     */
    Map<String, Object> awaitInputResponses(Map<String, InputRequest> inputRequests, Duration timeout)
            throws InterruptedException;

    default Optional<Object> awaitInputResponse(String key, String method, Object params, Duration timeout)
            throws InterruptedException
    {
        Map<String, Object> inputResponses = awaitInputResponses(ImmutableMap.of(key, new InputRequest(method, params)), timeout);
        return Optional.ofNullable(inputResponses.get(key));
    }
}
