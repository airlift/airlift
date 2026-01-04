package io.airlift.mcp.tasks;

import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import io.airlift.mcp.CancellationController;
import io.airlift.mcp.model.JsonRpcErrorDetail;
import io.airlift.mcp.model.JsonRpcMessage;
import io.airlift.mcp.model.JsonRpcRequest;
import io.airlift.mcp.model.JsonRpcResponse;
import io.airlift.mcp.model.Meta;
import io.airlift.mcp.sessions.BlockingResult;
import io.airlift.mcp.sessions.BlockingResult.Fulfilled;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.META_RELATED_TASK;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.sessions.SessionValueKey.taskFacadeKey;
import static io.airlift.mcp.tasks.TaskConditions.cancellationRequested;
import static java.util.Objects.requireNonNull;

public class Tasks
{
    private final SessionController sessionController;
    private final CancellationController cancellationController;
    private final TaskContextId taskContextId;
    private final Optional<Object> requestId;
    private final Optional<Object> progressToken;
    private final int defaultTaskTtlMs;
    private final int defaultPingThresholdMs;

    Tasks(SessionController sessionController, CancellationController cancellationController, TaskContextId taskContextId, Optional<Object> requestId, Optional<Object> progressToken, int defaultTaskTtlMs, int defaultPingThresholdMs)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.cancellationController = requireNonNull(cancellationController, "cancellationController is null");
        this.taskContextId = requireNonNull(taskContextId, "taskContextId is null");
        this.requestId = requireNonNull(requestId, "requestId is null");
        this.progressToken = requireNonNull(progressToken, "progressToken is null");

        this.defaultTaskTtlMs = defaultTaskTtlMs;
        this.defaultPingThresholdMs = defaultPingThresholdMs;
    }

    /**
     * Create a new task with default poll interval and TTL
     */
    public TaskFacade createTask()
    {
        return createTask(Optional.empty(), Optional.empty());
    }

    public TaskFacade createTask(Optional<Duration> pollInterval, Optional<Duration> ttl)
    {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        Object localRequestId = requestId.orElseThrow(() -> new IllegalStateException("Tasks cannot be created when the requestId is not present"));

        TaskFacade taskFacade = new TaskFacade(
                taskId,
                now,
                now,
                localRequestId,
                progressToken,
                Optional.empty(),
                pollInterval.map(Duration::toMillis).map(Math::toIntExact).orElse(defaultPingThresholdMs),
                Optional.empty(),
                ttl.map(Duration::toMillis).map(Math::toIntExact).orElse(defaultTaskTtlMs),
                Optional.empty(),
                false,
                ImmutableMap.of(),
                ImmutableMap.of());

        if (!sessionController.setSessionValue(TaskController.toSessionId(taskContextId), key, taskFacade)) {
            throw exception(INVALID_PARAMS, "Failed to create task in task context: " + taskContextId);
        }

        return taskFacade;
    }

    public void deleteTask(String taskId)
    {
        deleteTask(sessionController, taskContextId, taskId);
    }

    static void deleteTask(SessionController sessionController, TaskContextId taskContextId, String taskId)
    {
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        sessionController.deleteSessionValue(TaskController.toSessionId(taskContextId), key);
    }

    public Optional<TaskFacade> getTask(String taskId)
    {
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        return sessionController.getSessionValue(TaskController.toSessionId(taskContextId), key);
    }

    public void setTaskAttribute(String taskId, String attributeKey, String attributeValue)
    {
        SessionId sessionId = TaskController.toSessionId(taskContextId);
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        sessionController.computeSessionValue(sessionId, key, current -> {
            TaskFacade taskFacade = validateTask(taskContextId, taskId, current);
            return Optional.of(taskFacade.withAttribute(attributeKey, attributeValue));
        });
    }

    public void deleteTaskAttribute(String taskId, String attributeKey)
    {
        SessionId sessionId = TaskController.toSessionId(taskContextId);
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        sessionController.computeSessionValue(sessionId, key, current -> {
            TaskFacade taskFacade = validateTask(taskContextId, taskId, current);
            return Optional.of(taskFacade.withoutAttribute(attributeKey));
        });
    }

    public List<TaskFacade> listTasks(int pageSize, Optional<String> lastTaskId)
    {
        return sessionController.listSessionValues(TaskController.toSessionId(taskContextId), TaskFacade.class, pageSize, lastTaskId)
                .stream()
                .map(Map.Entry::getValue)
                .collect(toImmutableList());
    }

    public void clearTaskMessages(String taskId)
    {
        SessionId sessionId = TaskController.toSessionId(taskContextId);
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        sessionController.computeSessionValue(sessionId, key, current -> {
            TaskFacade taskFacade = validateModifyTask(taskContextId, taskId, current);
            taskFacade = taskFacade.withoutMessage();
            return Optional.of(taskFacade);
        });
    }

    public TaskFacade blockUntil(String taskId, Duration timeout, Duration pollInterval, Predicate<TaskFacade> condition)
            throws InterruptedException, TimeoutException
    {
        SessionId sessionId = TaskController.toSessionId(taskContextId);
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        long remainingMs = timeout.toMillis();

        while (remainingMs > 0) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            BlockingResult<TaskFacade> blockResult = sessionController.blockUntil(sessionId, key, pollInterval, maybeTask -> maybeTask.map(condition::test).orElse(false));
            if (blockResult instanceof Fulfilled<TaskFacade>(var task)) {
                return task;
            }

            remainingMs -= stopwatch.elapsed().toMillis();
        }

        throw new TimeoutException("Timeout waiting for task response: " + taskId);
    }

    @SuppressWarnings("unchecked")
    public void setTaskMessage(String taskId, JsonRpcMessage message, Optional<String> statusMessage)
    {
        SessionId sessionId = TaskController.toSessionId(taskContextId);
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        sessionController.computeSessionValue(sessionId, key, current -> {
            TaskFacade taskFacade = validateModifyTask(taskContextId, taskId, current);

            switch (message) {
                case JsonRpcRequest<?> rpcRequest -> {
                    validateMetaObject(taskId, (Optional<Object>) rpcRequest.params());
                    taskFacade = taskFacade.withMessage(message, statusMessage);
                }

                case JsonRpcResponse<?> rpcResponse -> {
                    validateMetaObject(taskId, (Optional<Object>) rpcResponse.result());
                    taskFacade = taskFacade.withMessage(message, statusMessage).asCompleted();
                }
            }

            return Optional.of(taskFacade);
        });
    }

    public void requestTaskCancellation(String taskId, Optional<String> statusMessage)
    {
        SessionId sessionId = TaskController.toSessionId(taskContextId);
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        sessionController.computeSessionValue(sessionId, key, current -> {
            TaskFacade taskFacade = validateModifyTask(taskContextId, taskId, current);
            return Optional.of(taskFacade.withCancellationRequested(statusMessage));
        });
    }

    public void completeTask(String taskId, JsonRpcResponse<?> response, Optional<String> statusMessage)
    {
        SessionId sessionId = TaskController.toSessionId(taskContextId);
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        sessionController.computeSessionValue(sessionId, key, current -> {
            TaskFacade taskFacade = validateTask(taskContextId, taskId, current);
            validateTaskNotCompleted(taskContextId, taskId, taskFacade);

            taskFacade = taskFacade.withMessage(response, statusMessage).asCompleted();

            return Optional.of(taskFacade);
        });
    }

    public void addServerToClientResponse(String taskId, JsonRpcResponse<?> response)
    {
        SessionId sessionId = TaskController.toSessionId(taskContextId);
        SessionValueKey<TaskFacade> key = taskFacadeKey(taskId);

        sessionController.computeSessionValue(sessionId, key, current -> {
            TaskFacade taskFacade = validateTask(taskContextId, taskId, current);
            validateTaskNotCompleted(taskContextId, taskId, taskFacade);

            taskFacade = taskFacade.withResponse(response);

            return Optional.of(taskFacade);
        });
    }

    public void executeCancellable(String taskId, Runnable runnable)
    {
        executeCancellable(taskId, () -> {
            runnable.run();
            return null;
        });
    }

    public <T> T executeCancellable(String taskId, Supplier<T> supplier)
    {
        return cancellationController.builder(TaskController.toSessionId(taskContextId), taskFacadeKey(taskId))
                .withTaskId(taskId)
                .withIsCancelledCondition(cancellationRequested)
                .withPostCancellationAction((sessionId, key) -> sessionController.computeSessionValue(sessionId, key, current -> {
                    TaskFacade taskFacade = validateTask(taskContextId, taskId, current);
                    validateTaskNotCompleted(taskContextId, taskId, taskFacade);

                    JsonRpcErrorDetail error = new JsonRpcErrorDetail(INVALID_REQUEST, "Task was cancelled");
                    JsonRpcResponse<?> response = new JsonRpcResponse<>(taskFacade.requestId(), Optional.of(error), Optional.empty());
                    taskFacade = taskFacade.withMessage(response, Optional.of("Task was cancelled")).asCompleted();
                    return Optional.of(taskFacade);
                }))
                .executeCancellable(supplier);
    }

    private static TaskFacade validateModifyTask(TaskContextId taskContextId, String taskId, Optional<TaskFacade> current)
    {
        TaskFacade taskFacade = validateTask(taskContextId, taskId, current);

        if (taskFacade.cancellationRequested()) {
            throw exception(INVALID_PARAMS, "Task has been cancelled. TaskContextId: %s, TaskId: %s ".formatted(taskContextId, taskId));
        }

        validateTaskNotCompleted(taskContextId, taskId, taskFacade);

        return taskFacade;
    }

    private static void validateTaskNotCompleted(TaskContextId taskContextId, String taskId, TaskFacade taskFacade)
    {
        if (taskFacade.completedAt().isPresent()) {
            throw exception(INVALID_PARAMS, "Cannot set message on completed task. TaskContextId: %s, TaskId: %s ".formatted(taskContextId, taskId));
        }
    }

    private static TaskFacade validateTask(TaskContextId taskContextId, String taskId, Optional<TaskFacade> current)
    {
        return current.orElseThrow(() -> exception(INVALID_PARAMS, "Task does not exist. TaskContextId: %s, TaskId: %s ".formatted(taskContextId, taskId)));
    }

    private static void validateMetaObject(String taskId, Optional<Object> maybeObj)
    {
        maybeObj.ifPresent(obj -> {
            if (obj instanceof Meta meta) {
                validateMeta(taskId, meta);
            }
        });
    }

    private static void validateMeta(String taskId, Meta meta)
    {
        Object relatedTaskId = meta.meta().map(values -> values.get(META_RELATED_TASK)).orElse(null);
        if (relatedTaskId == null) {
            throw exception(INVALID_PARAMS, "Missing related task ID in meta");
        }
        if (!taskId.equals(relatedTaskId.toString())) {
            throw exception(INVALID_PARAMS, "Task does not match related task ID. Expected: %s, Found: %s".formatted(taskId, relatedTaskId));
        }
    }
}
