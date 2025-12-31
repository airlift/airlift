package io.airlift.mcp.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.model.CallToolResult;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.model.TaskStatus;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.META_RELATED_TASK;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.sessions.SessionValueKey.taskAdapterKey;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

public class TaskController
{
    private static final Logger log = Logger.get(TaskController.class);

    private static final int PAGE_SIZE = 100;

    private final Optional<SessionController> sessionController;
    private final ScheduledExecutorService executorService;
    private final Duration cleanupInterval;
    private final Duration defaultTaskTtl;
    private final Duration abandonedTaskThreshold;

    @Inject
    public TaskController(Optional<SessionController> sessionController, McpConfig config)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");

        executorService = Executors.newSingleThreadScheduledExecutor(daemonThreadsNamed("task-controller"));

        defaultTaskTtl = config.getDefaultTaskTtl().toJavaTime();
        cleanupInterval = config.getTaskCleanupInterval().toJavaTime();
        abandonedTaskThreshold = config.getAbandonedTaskThreshold().toJavaTime();
    }

    @PostConstruct
    public void start()
    {
        sessionController.ifPresent(controller ->
                executorService.scheduleAtFixedRate(() -> cleanupExpiredTasks(controller), cleanupInterval.toMillis(), cleanupInterval.toMillis(), TimeUnit.MILLISECONDS));
    }

    @PreDestroy
    public void stop()
    {
        if (!shutdownAndAwaitTermination(executorService, 10, TimeUnit.SECONDS)) {
            log.warn("TaskController executor did not terminate");
        }
    }

    public TaskContextId newTaskContextId(Optional<McpIdentity> identity)
    {
        SessionController sessionController = requireSessionController();

        SessionId sessionId = sessionController.createSession(identity, Optional.empty());
        return new TaskContextId(sessionId.id());
    }

    public boolean validateTaskContextId(TaskContextId taskContextId)
    {
        SessionController sessionController = requireSessionController();

        return sessionController.validateSession(toSessionId(taskContextId));
    }

    public void deleteTaskContext(TaskContextId taskContextId)
    {
        SessionController sessionController = requireSessionController();
        SessionId sessionId = toSessionId(taskContextId);

        sessionController.deleteSession(sessionId);
    }

    public TaskAdapter createTask(TaskContextId taskContextId, Object requestId, Map<String, String> attributes, Optional<Object> progressToken, OptionalInt pollInterval, OptionalInt ttl)
    {
        SessionController sessionController = requireSessionController();
        SessionId sessionId = toSessionId(taskContextId);

        String taskId = UUID.randomUUID().toString().replace("-", "");
        SessionValueKey<TaskAdapter> taskHolderKey = taskAdapterKey(taskId);

        TaskAdapter newTask = new TaskAdapter(taskId, Instant.now(), requestId, attributes, progressToken, pollInterval, ttl);
        sessionController.setSessionValue(sessionId, taskHolderKey, newTask);

        return newTask;
    }

    public Optional<TaskAdapter> getTask(TaskContextId taskContextId, String taskId)
    {
        SessionController sessionController = requireSessionController();
        SessionId sessionId = toSessionId(taskContextId);

        SessionValueKey<TaskAdapter> taskHolderKey = taskAdapterKey(taskId);
        return sessionController.getSessionValue(sessionId, taskHolderKey);
    }

    @SuppressWarnings("BusyWait")
    public TaskAdapter blockUntilCondition(TaskContextId taskContextId, String taskId, Duration timeout, Duration pollInterval, Runnable pollProc, Predicate<TaskAdapter> condition)
            throws InterruptedException, TimeoutException
    {
        Stopwatch stopwatch = Stopwatch.createStarted();
        while (stopwatch.elapsed().compareTo(timeout) < 0) {
            TaskAdapter task = getTask(taskContextId, taskId)
                    .orElseThrow(() -> exception(INVALID_PARAMS, "Task %s does not exist", taskId));
            if (condition.test(task)) {
                return task;
            }

            try {
                Thread.sleep(pollInterval.toMillis());
                pollProc.run();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        throw new TimeoutException("Timed out waiting for server to client response");
    }

    public List<TaskAdapter> listTasks(TaskContextId taskContextId, int pageSize, Optional<String> taskIdCursor)
    {
        SessionController sessionController = requireSessionController();
        SessionId sessionId = toSessionId(taskContextId);

        return sessionController.listSessionValues(sessionId, TaskAdapter.class, pageSize, taskIdCursor)
                .stream()
                .map(Map.Entry::getValue)
                .collect(toImmutableList());
    }

    public boolean deleteTask(TaskContextId taskContextId, String taskId)
    {
        SessionController sessionController = requireSessionController();
        SessionId sessionId = toSessionId(taskContextId);

        SessionValueKey<TaskAdapter> taskHolderKey = taskAdapterKey(taskId);
        return sessionController.deleteSessionValue(sessionId, taskHolderKey);
    }

    public static JsonRpcMessage buildResult(TaskAdapter task, CallToolResult callToolResult)
    {
        return new JsonRpcResponse<>(task.requestId(), Optional.empty(), Optional.of(callToolResult));
    }

    public static JsonRpcMessage buildError(TaskAdapter task, JsonRpcErrorDetail errorDetail)
    {
        return new JsonRpcResponse<>(task.requestId(), Optional.of(errorDetail), Optional.empty());
    }

    public boolean setTaskAttributes(TaskContextId taskContextId, String taskId, Map<String, String> attributes)
    {
        SessionController sessionController = requireSessionController();
        SessionId sessionId = toSessionId(taskContextId);

        SessionValueKey<TaskAdapter> taskHolderKey = taskAdapterKey(taskId);
        return sessionController.computeSessionValue(sessionId, taskHolderKey, current -> current.map(task -> task.withAttributes(attributes)));
    }

    @SuppressWarnings("unchecked")
    public boolean setTaskMessage(TaskContextId taskContextId, String taskId, Optional<JsonRpcMessage> message, Optional<String> statusMessage, boolean cancellationRequested)
    {
        SessionController sessionController = requireSessionController();
        SessionId sessionId = toSessionId(taskContextId);

        boolean isResponse = message.map(internalMessage -> switch (internalMessage) {
            case JsonRpcRequest<?> jsonRpcRequest -> {
                validateMeta(taskId, (Optional<Object>) jsonRpcRequest.params());
                yield false;
            }
            case JsonRpcResponse<?> jsonRpcResponse -> {
                validateMeta(taskId, (Optional<Object>) jsonRpcResponse.result());
                yield true;
            }
        }).orElse(false);

        SessionValueKey<TaskAdapter> taskHolderKey = taskAdapterKey(taskId);
        return sessionController.computeSessionValue(sessionId, taskHolderKey, current -> {
            if (current.isEmpty()) {
                throw exception(INVALID_PARAMS, "Task %s does not exist", taskId);
            }

            TaskAdapter task = current.get();
            TaskStatus taskStatus = task.toTaskStatus();

            if ((taskStatus != TaskStatus.WORKING) && (taskStatus != TaskStatus.INPUT_REQUIRED)) {
                throw exception(INVALID_PARAMS, "Task %s is already completed with status %s".formatted(taskId, taskStatus));
            }

            TaskAdapter updatedTask = new TaskAdapter(
                    task.taskId(),
                    task.createdAt(),
                    Instant.now(),
                    task.requestId(),
                    task.attributes(),
                    task.progressToken(),
                    isResponse ? Optional.of(Instant.now()) : Optional.empty(),
                    task.pollInterval(),
                    statusMessage,
                    task.ttl(),
                    message,
                    cancellationRequested,
                    task.responses());

            return Optional.of(updatedTask);
        });
    }

    @SuppressWarnings("unchecked")
    public void addTaskResponse(TaskContextId taskContextId, String taskId, JsonRpcResponse<?> response)
    {
        validateMeta(taskId, (Optional<Object>) response.result());

        SessionController sessionController = requireSessionController();
        SessionId sessionId = toSessionId(taskContextId);

        SessionValueKey<TaskAdapter> taskHolderKey = taskAdapterKey(taskId);
        sessionController.computeSessionValue(sessionId, taskHolderKey, current -> {
            if (current.isEmpty()) {
                throw exception(INVALID_PARAMS, "Task %s does not exist", taskId);
            }

            TaskAdapter task = current.get();

            TaskAdapter updatedTask = task.withResponse(response);

            if (task.message().isPresent()) {
                Object messageId = task.message().get().id();
                if (response.id().equals(messageId)) {
                    // we have the response for the current outgoing message, clear it
                    updatedTask = updatedTask.withoutMessage();
                }
            }

            return Optional.of(updatedTask);
        });
    }

    public SessionId toSessionId(TaskContextId taskContextId)
    {
        return new SessionId(taskContextId.id());
    }

    private void cleanupExpiredTasks(SessionController sessionController)
    {
        Optional<SessionId> cursor = Optional.empty();
        do {
            List<SessionId> sessionIds = sessionController.listSessions(PAGE_SIZE, cursor);
            sessionIds.forEach(sessionId -> cleanupExpiredTasks(sessionController, sessionId));
            cursor = (sessionIds.size() < PAGE_SIZE) ? Optional.empty() : Optional.of(sessionIds.getLast());
        }
        while (cursor.isPresent());
    }

    private void cleanupExpiredTasks(SessionController sessionController, SessionId sessionId)
    {
        TaskContextId taskContextId = new TaskContextId(sessionId.id());

        Optional<String> cursor = Optional.empty();
        do {
            List<Map.Entry<String, TaskAdapter>> tasks = sessionController.listSessionValues(sessionId, TaskAdapter.class, PAGE_SIZE, cursor);
            tasks.forEach(entry -> cleanupIfTaskExpired(taskContextId, entry.getKey(), entry.getValue()));
            cursor = (tasks.size() < PAGE_SIZE) ? Optional.empty() : Optional.of(tasks.getLast().getKey());
        }
        while (cursor.isPresent());
    }

    private void cleanupIfTaskExpired(TaskContextId taskContextId, String taskId, TaskAdapter task)
    {
        Instant now = Instant.now();

        boolean deleteIt = task.completeAt().map(completeAt -> {
            int ttlMs = task.ttl().orElseGet(() -> toIntExact(defaultTaskTtl.toMillis()));

            Duration elapsedSinceCompletion = Duration.between(completeAt, now);
            if (elapsedSinceCompletion.toMillis() >= ttlMs) {
                log.info("Cleaning up expired task. Context %s, TaskL %s", taskContextId.id(), task);
                return true;
            }
            return false;
        }).orElseGet(() -> {
            Duration taskAge = Duration.between(task.createdAt(), now);
            if (taskAge.compareTo(abandonedTaskThreshold) >= 0) {
                log.info("Cleaning up abandoned task. Context %s. Task: %s", taskContextId.id(), task);
                return true;
            }
            return false;
        });

        if (deleteIt && !deleteTask(taskContextId, taskId)) {
            log.warn("Failed to delete expired task. Context %s. Task: %s", taskContextId.id(), task);
        }
    }

    private void validateMeta(String taskId, Optional<Object> maybeMeta)
    {
        maybeMeta.ifPresent(meta -> {
            if (meta instanceof Meta metaInstance) {
                Object relatedTask = metaInstance.meta().map(values -> values.get(META_RELATED_TASK)).orElse(null);
                if (relatedTask == null) {
                    throw exception(INVALID_PARAMS, "Meta %s is missing".formatted(META_RELATED_TASK));
                }
                if (!taskId.equals(String.valueOf(relatedTask))) {
                    throw exception(INVALID_PARAMS, "Meta %s %s does not match task ID: %s".formatted(META_RELATED_TASK, relatedTask, taskId));
                }
            }
        });
    }

    private SessionController requireSessionController()
    {
        return sessionController.orElseThrow(() -> new IllegalStateException("SessionController is not available"));
    }
}
