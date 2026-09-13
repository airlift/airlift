package io.airlift.mcp.tasks.memory;

import com.google.common.collect.ImmutableMap;
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
import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

class MemoryTask
        implements TaskContext
{
    private final String taskId;
    private final Authenticated<?> identity;
    private final CallToolRequest callToolRequest;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final int ttlMs;
    private final int pollIntervalMs;

    @GuardedBy("this")
    private TaskStatus status = TaskStatus.WORKING;
    @GuardedBy("this")
    private Instant lastUpdatedAt;
    @GuardedBy("this")
    private Optional<String> statusMessage = Optional.empty();
    @GuardedBy("this")
    private Map<String, InputRequest> inputRequests = ImmutableMap.of();
    @GuardedBy("this")
    private Map<String, Object> inputResponses = new LinkedHashMap<>();
    @GuardedBy("this")
    private Optional<CallToolResult> result = Optional.empty();
    @GuardedBy("this")
    private Optional<JsonRpcErrorDetail> error = Optional.empty();
    @GuardedBy("this")
    private boolean cancellationRequested;
    @GuardedBy("this")
    private Optional<Thread> thread = Optional.empty();
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
        ttlMs = toIntExact(ttl.toMillis());
        pollIntervalMs = toIntExact(pollInterval.toMillis());
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

        this.inputRequests = ImmutableMap.copyOf(inputRequests);
        inputResponses = new LinkedHashMap<>();
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
                if (status == TaskStatus.INPUT_REQUIRED) {
                    status = TaskStatus.WORKING;
                }
                touch();
            }

            if (cancellationRequested) {
                throw new InterruptedException("Task %s was cancelled".formatted(taskId));
            }

            return ImmutableMap.copyOf(inputResponses);
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
     * @return {@code true} when the executor is waiting for input it has now all been given -
     *         nothing is running the task, so it must be run again
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

        return inputRequests.isEmpty() && thread.isEmpty() && !status.isTerminal();
    }

    void execute(TaskExecutor executor)
    {
        synchronized (this) {
            this.executor = Optional.of(executor);
        }

        setExecutingThread(Optional.of(Thread.currentThread()));
        try {
            settle(TaskRunner.run(executor, identity, callToolRequest, this));
        }
        finally {
            setExecutingThread(Optional.empty());
        }
    }

    synchronized Optional<TaskExecutor> executor()
    {
        return executor;
    }

    private synchronized void setExecutingThread(Optional<Thread> executingThread)
    {
        thread = executingThread;
        if (cancellationRequested) {
            thread.ifPresent(Thread::interrupt);
        }
    }

    synchronized void requestCancellation()
    {
        if (status.isTerminal()) {
            return;
        }

        cancellationRequested = true;
        thread.ifPresent(Thread::interrupt);
        notifyAll();
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
                OptionalInt.of(ttlMs),
                OptionalInt.of(pollIntervalMs),
                (status == TaskStatus.INPUT_REQUIRED) ? Optional.of(inputRequests) : Optional.empty(),
                result,
                error);
    }

    private synchronized void touch()
    {
        lastUpdatedAt = Instant.now();
    }
}
