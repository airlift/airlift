package io.airlift.mcp.tasks.memory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Closer;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.McpTasks;
import io.airlift.mcp.model.CallToolRequest;
import io.airlift.mcp.model.Task;
import io.airlift.mcp.tasks.TaskExecutor;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.concurrent.Threads.virtualThreadsNamed;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.Executors.newThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * An {@link McpTasks} that keeps tasks in memory and runs their handlers on virtual threads in
 * this process. Tasks do not survive a restart and are not visible to other instances of the
 * server.
 */
public class MemoryTaskEngine
        implements McpTasks, AutoCloseable
{
    private static final Logger log = Logger.get(MemoryTaskEngine.class);

    private static final Duration PURGE_PERIOD = Duration.ofMinutes(1);

    private final Map<String, MemoryTask> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executorService;
    private final ScheduledExecutorService purgeExecutor = newSingleThreadScheduledExecutor(virtualThreadsNamed("mcp-task-purge-%s"));
    private volatile boolean closed;

    @Inject
    public MemoryTaskEngine()
    {
        this(newThreadPerTaskExecutor(virtualThreadsNamed("mcp-task-%s")), PURGE_PERIOD);
    }

    @VisibleForTesting
    MemoryTaskEngine(ExecutorService executorService, Duration purgePeriod)
    {
        this.executorService = requireNonNull(executorService, "executorService is null");

        // started here rather than by the lifecycle, so expired tasks are purged however the engine is created
        purgeExecutor.scheduleWithFixedDelay(this::purgeExpiredTasks, purgePeriod.toMillis(), purgePeriod.toMillis(), MILLISECONDS);
    }

    @PreDestroy
    @Override
    public void close()
    {
        closed = true;

        try {
            // a Closer closes in reverse: the executor is shut down last, once every task has been told to stop
            Closer closer = Closer.create();

            closer.register(() -> {
                if (!shutdownAndAwaitTermination(executorService, 10, SECONDS)) {
                    log.warn("Failed to shut down the task executor");
                }
            });

            tasks.values().forEach(task -> closer.register(task::requestCancellation));

            closer.register(purgeExecutor::shutdownNow);

            closer.close();
        }
        catch (Exception e) {
            log.warn(e, "Failed to cancel tasks");
        }
        finally {
            tasks.clear();
        }
    }

    @Override
    public Task createTask(McpRequestContext requestContext, CallToolRequest callToolRequest, Duration ttl, Duration pollInterval)
    {
        // a closed engine could store the task but never run or purge it
        checkState(!closed, "Task engine is closed");

        MemoryTask task = new MemoryTask(UUID.randomUUID().toString(), requestContext.identity(), callToolRequest, ttl, pollInterval);
        tasks.put(task.taskId(), task);
        return task.toTask();
    }

    @Override
    public void executeTask(String taskId, TaskExecutor executor)
    {
        findTask(taskId).ifPresent(task -> submitAttempt(task, executor));
    }

    @Override
    public boolean validateTaskAccess(McpRequestContext requestContext, String taskId)
    {
        return findTask(taskId)
                // an unknown task and another caller's task are indistinguishable to the caller
                .map(task -> task.identity().equals(requestContext.identity()))
                .orElse(false);
    }

    @Override
    public Optional<Task> getTask(String taskId)
    {
        return findTask(taskId).map(MemoryTask::toTask);
    }

    @Override
    public boolean updateTask(String taskId, Map<String, Object> inputResponses)
    {
        return findTask(taskId)
                .map(task -> {
                    if (task.deliverInputResponses(inputResponses)) {
                        task.executor().ifPresent(executor -> submitAttempt(task, executor));
                    }
                    return true;
                })
                .orElse(false);
    }

    @Override
    public boolean cancelTask(String taskId)
    {
        return findTask(taskId)
                .map(task -> {
                    task.requestCancellation();
                    return true;
                })
                .orElse(false);
    }

    private void submitAttempt(MemoryTask task, TaskExecutor executor)
    {
        try {
            executorService.submit(() -> runAttempt(task, executor));
        }
        catch (RejectedExecutionException e) {
            // the engine is closing, which cancels every task - one it can no longer run is no different
            task.requestCancellation();
        }
    }

    private void runAttempt(MemoryTask task, TaskExecutor executor)
    {
        // input responses that arrive while an attempt is unwinding resume the task on a new thread, so an
        // interrupt the attempt left set - or any other state of its thread - does not carry into the next one
        if (task.execute(executor)) {
            submitAttempt(task, executor);
        }
    }

    private Optional<MemoryTask> findTask(String taskId)
    {
        requireNonNull(taskId, "taskId is null");

        MemoryTask task = tasks.get(taskId);
        if (task == null) {
            return Optional.empty();
        }
        if (task.hasExpired()) {
            purge(task);
            return Optional.empty();
        }
        return Optional.of(task);
    }

    private void purgeExpiredTasks()
    {
        tasks.values()
                .stream()
                .filter(MemoryTask::hasExpired)
                .forEach(this::purge);
    }

    private void purge(MemoryTask task)
    {
        tasks.remove(task.taskId());
        task.requestCancellation();
    }
}
