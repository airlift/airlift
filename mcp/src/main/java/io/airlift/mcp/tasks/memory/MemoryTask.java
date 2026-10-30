package io.airlift.mcp.tasks.memory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import io.airlift.mcp.McpIdentity.Authenticated;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.InputRequest;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.model.TaskResult;
import io.airlift.mcp.model.TaskResult.Cancelled;
import io.airlift.mcp.model.TaskResult.Failed;
import io.airlift.mcp.model.TaskResult.Suspended;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.tasks.TaskContext;
import io.airlift.mcp.tasks.TaskExecutor;
import io.airlift.mcp.tasks.TaskRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

class MemoryTask
        implements TaskContext
{
    private final String taskId;
    private final Authenticated<?> identity;
    private final CallToolRequest callToolRequest;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final long ttlMs;
    private final long pollIntervalMs;

    @GuardedBy("this")
    private TaskStatus status = TaskStatus.WORKING;
    @GuardedBy("this")
    private Instant lastUpdatedAt;
    @GuardedBy("this")
    private Optional<String> statusMessage = Optional.empty();
    @GuardedBy("this")
    private Map<String, InputRequest> inputRequests = ImmutableMap.of();
    @GuardedBy("this")
    private final Map<String, Object> inputResponses = new LinkedHashMap<>();
    @GuardedBy("this")
    private Optional<CallToolResult> result = Optional.empty();
    @GuardedBy("this")
    private Optional<JsonRpcErrorDetail> error = Optional.empty();
    @GuardedBy("this")
    private boolean cancellationRequested;
    @GuardedBy("this")
    private Optional<Thread> thread = Optional.empty();
    @GuardedBy("this")
    private boolean inputResponsesArrived;
    @GuardedBy("this")
    private Optional<TaskExecutor> executor = Optional.empty();

    MemoryTask(String taskId, Authenticated<?> identity, CallToolRequest callToolRequest, Duration ttl, Duration pollInterval)
    {
        this.taskId = requireNonNull(taskId, "taskId is null");
        this.identity = requireNonNull(identity, "identity is null");
        this.callToolRequest = requireNonNull(callToolRequest, "callToolRequest is null");

        createdAt = Instant.now();
        lastUpdatedAt = createdAt;
        expiresAt = createdAt.plus(ttl);
        ttlMs = ttl.toMillis();
        pollIntervalMs = pollInterval.toMillis();
    }

    @Override
    public String taskId()
    {
        return taskId;
    }

    @Override
    public synchronized void setStatusMessage(String statusMessage)
    {
        requireNonNull(statusMessage, "statusMessage is null");

        if (status.isTerminal()) {
            return;
        }
        this.statusMessage = Optional.of(statusMessage);
        touch();
    }

    @Override
    public synchronized boolean isCancellationRequested()
    {
        return cancellationRequested;
    }

    @Override
    public synchronized void requestInput(Map<String, InputRequest> inputRequests)
    {
        requireNonNull(inputRequests, "inputRequests is null");
        checkState(!inputRequests.isEmpty(), "inputRequests is empty");
        checkState(status == TaskStatus.WORKING, "Task %s is %s - it cannot request input", taskId, status);
        // a suspended executor is run from the top again and reads the answers by key, so a re-used key would hide the earlier answer
        inputRequests.keySet().forEach(key -> checkState(!inputResponses.containsKey(key), "Input request %s of task %s was already answered", key, taskId));

        this.inputRequests = ImmutableMap.copyOf(inputRequests);
        status = TaskStatus.INPUT_REQUIRED;
        touch();
    }

    @Override
    public synchronized Map<String, Object> inputResponses()
    {
        return ImmutableMap.copyOf(inputResponses);
    }

    @Override
    public Map<String, Object> awaitInputResponses(Map<String, InputRequest> inputRequests, Duration timeout)
            throws InterruptedException
    {
        requireNonNull(timeout, "timeout is null");

        long deadline = System.nanoTime() + timeout.toNanos();

        synchronized (this) {
            requestInput(inputRequests);
            Set<String> requested = ImmutableSet.copyOf(inputRequests.keySet());

            try {
                while (!this.inputRequests.isEmpty() && !cancellationRequested) {
                    long remaining = Duration.ofNanos(deadline - System.nanoTime()).toMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    wait(remaining);
                }
            }
            finally {
                this.inputRequests = ImmutableMap.of();
                // this thread consumed the answers, so they must not resume the task as well
                inputResponsesArrived = false;
                if (status == TaskStatus.INPUT_REQUIRED) {
                    status = TaskStatus.WORKING;
                }
                touch();
            }

            if (cancellationRequested) {
                throw new InterruptedException("Task %s was cancelled".formatted(taskId));
            }

            return ImmutableMap.copyOf(Maps.filterKeys(inputResponses, requested::contains));
        }
    }

    Authenticated<?> identity()
    {
        return identity;
    }

    boolean hasExpired()
    {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * @return {@code true} when the executor is waiting for input it has now all been given and
     *         nothing is running the task, so the caller must run it again. When an attempt is
     *         still unwinding it claims the resume itself as it ends, and this returns
     *         {@code false}.
     */
    synchronized boolean deliverInputResponses(Map<String, Object> responses)
    {
        if (inputRequests.isEmpty()) {
            return false;
        }

        Map<String, InputRequest> stillPending = new HashMap<>(inputRequests);
        responses.forEach((key, response) -> {
            if (stillPending.remove(key) != null) {
                inputResponses.put(key, response);
            }
        });

        inputRequests = ImmutableMap.copyOf(stillPending);
        if (inputRequests.isEmpty() && (status == TaskStatus.INPUT_REQUIRED)) {
            status = TaskStatus.WORKING;
        }
        touch();
        notifyAll();

        if (!inputRequests.isEmpty() || status.isTerminal()) {
            return false;
        }
        if (thread.isPresent()) {
            inputResponsesArrived = true;
            return false;
        }
        return true;
    }

    /**
     * @return {@code true} when input responses arrived while this attempt was unwinding, so the
     *         task must be run again
     */
    boolean execute(TaskExecutor executor)
    {
        if (!beginAttempt(executor)) {
            return false;
        }

        boolean resume;
        try {
            settle(TaskRunner.run(executor, identity, callToolRequest, this));
        }
        finally {
            resume = endAttempt();
        }
        return resume;
    }

    /**
     * @return {@code true} when this attempt owns the task and the tool call must be made. A task
     *         that was cancelled before the attempt began is settled here instead - the tool is
     *         never called, so it cannot have side effects after the task is terminal. A task that
     *         another attempt is already running, or that is waiting on the client, is left alone -
     *         the tool runs once at a time.
     */
    private synchronized boolean beginAttempt(TaskExecutor executor)
    {
        if ((status != TaskStatus.WORKING) || thread.isPresent()) {
            return false;
        }
        if (cancellationRequested) {
            settle(Cancelled.INSTANCE);
            return false;
        }

        this.executor = Optional.of(executor);
        thread = Optional.of(Thread.currentThread());
        return true;
    }

    private synchronized boolean endAttempt()
    {
        thread = Optional.empty();

        // nothing is running the task now, so a cancellation it did not honor has to be settled here
        if (cancellationRequested) {
            settle(Cancelled.INSTANCE);
            return false;
        }

        boolean resume = inputResponsesArrived && (status == TaskStatus.WORKING);
        inputResponsesArrived = false;
        return resume;
    }

    synchronized Optional<TaskExecutor> executor()
    {
        return executor;
    }

    synchronized void requestCancellation()
    {
        if (status.isTerminal()) {
            return;
        }

        cancellationRequested = true;
        notifyAll();

        // a suspended task has no thread to interrupt and nothing that would settle it
        thread.ifPresentOrElse(Thread::interrupt, () -> settle(Cancelled.INSTANCE));
    }

    private synchronized void settle(TaskResult taskResult)
    {
        if (status.isTerminal()) {
            return;
        }

        switch (taskResult) {
            case Suspended _ -> {}

            case CallToolResult callToolResult -> {
                result = Optional.of(callToolResult);
                postSettle(TaskStatus.COMPLETED);
            }

            case Failed(JsonRpcErrorDetail errorDetail) -> {
                error = Optional.of(errorDetail);
                statusMessage = Optional.of(errorDetail.message());
                postSettle(TaskStatus.FAILED);
            }

            case Cancelled _ -> postSettle(TaskStatus.CANCELLED);
        }
    }

    private synchronized void postSettle(TaskStatus newStatus)
    {
        status = newStatus;
        inputRequests = ImmutableMap.of();
        touch();
        notifyAll();
    }

    synchronized Task toTask()
    {
        return new Task(
                taskId,
                status,
                statusMessage,
                createdAt.toString(),
                lastUpdatedAt.toString(),
                OptionalLong.of(ttlMs),
                OptionalLong.of(pollIntervalMs),
                (status == TaskStatus.INPUT_REQUIRED) ? Optional.of(inputRequests) : Optional.empty(),
                result,
                error);
    }

    private synchronized void touch()
    {
        lastUpdatedAt = Instant.now();
    }
}
