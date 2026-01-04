package io.airlift.mcp.tasks;

import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.CancellationController;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpIdentity;
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.model.Constants.META_RELATED_TASK;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_PARAMS;
import static io.airlift.mcp.model.JsonRpcErrorCode.INVALID_REQUEST;
import static io.airlift.mcp.sessions.SessionController.FOREVER_TTL;
import static io.airlift.mcp.sessions.SessionValueKey.taskAdapterKey;
import static io.airlift.mcp.tasks.TaskCondition.cancellationRequested;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

class TaskControllerImpl
        implements TaskController, TaskContextController
{
    private static final Logger log = Logger.get(TaskControllerImpl.class);

    private static final int CLEANUP_PAGE_SIZE = 100;

    private final SessionController sessionController;
    private final CancellationController cancellationController;
    private final Duration abandonedTaskThreshold;
    private final int defaultTaskTtlMs;
    private final int defaultPingThresholdMs;

    @Inject
    TaskControllerImpl(SessionController sessionController, CancellationController cancellationController, McpConfig mcpConfig)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.cancellationController = requireNonNull(cancellationController, "cancellationController is null");

        defaultTaskTtlMs = toIntExact(mcpConfig.getDefaultTaskTtl().toMillis());
        defaultPingThresholdMs = toIntExact(mcpConfig.getEventStreamingPingThreshold().toMillis());
        abandonedTaskThreshold = mcpConfig.getAbandonedTaskThreshold().toJavaTime();
    }

    @Override
    public TaskContextId createTaskContext(McpIdentity identity)
    {
        return new TaskContextIdImpl(sessionController.createSession(identity, FOREVER_TTL).id());
    }

    @Override
    public boolean validateTaskContext(TaskContextId taskContextId)
    {
        if (!sessionController.validateSession(toSessionId(taskContextId))) {
            return false;
        }

        cleanExpiredTasks(taskContextId);

        return true;
    }

    @Override
    public void deleteTaskContext(TaskContextId taskContextId)
    {
        sessionController.deleteSession(toSessionId(taskContextId));
    }

    @Override
    public TaskAdapter createTask(NewTask newTask)
    {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        SessionValueKey<TaskAdapter> taskAdapterKey = taskAdapterKey(taskId);

        TaskAdapter taskAdapter = new TaskAdapter(
                taskId,
                now,
                now,
                newTask.requestId(),
                newTask.attributes(),
                newTask.progressToken(),
                Optional.empty(),
                newTask.pollInterval().orElse(defaultPingThresholdMs),
                Optional.empty(),
                newTask.ttl().orElse(defaultTaskTtlMs),
                Optional.empty(),
                false,
                ImmutableMap.of());

        if (!sessionController.setSessionValue(toSessionId(newTask.taskContextId()), taskAdapterKey, taskAdapter)) {
            throw exception(INVALID_PARAMS, "Failed to create task in task context: " + newTask.taskContextId());
        }

        return taskAdapter;
    }

    @Override
    public void deleteTask(TaskContextId taskContextId, String taskId)
    {
        SessionValueKey<TaskAdapter> taskAdapterKey = taskAdapterKey(taskId);

        sessionController.deleteSessionValue(toSessionId(taskContextId), taskAdapterKey);
    }

    @Override
    public Optional<TaskAdapter> getTask(TaskContextId taskContextId, String taskId)
    {
        SessionValueKey<TaskAdapter> taskAdapterKey = taskAdapterKey(taskId);

        return sessionController.getSessionValue(toSessionId(taskContextId), taskAdapterKey);
    }

    @Override
    public List<TaskAdapter> listTasks(TaskContextId taskContextId, int pageSize, Optional<String> lastTaskId)
    {
        return sessionController.listSessionValues(toSessionId(taskContextId), TaskAdapter.class, pageSize, lastTaskId)
                .stream()
                .map(Map.Entry::getValue)
                .collect(toImmutableList());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setTaskMessage(TaskContextId taskContextId, String taskId, JsonRpcMessage message, Optional<String> statusMessage)
    {
        SessionId sessionId = toSessionId(taskContextId);
        SessionValueKey<TaskAdapter> taskAdapterKey = taskAdapterKey(taskId);

        sessionController.computeSessionValue(sessionId, taskAdapterKey, current -> {
            TaskAdapter taskAdapter = validateModifyTask(taskContextId, taskId, current);

            switch (message) {
                case JsonRpcRequest<?> rpcRequest -> {
                    validateMetaObject(taskId, (Optional<Object>) rpcRequest.params());
                    taskAdapter = taskAdapter.withMessage(message, statusMessage);
                }

                case JsonRpcResponse<?> rpcResponse -> {
                    validateMetaObject(taskId, (Optional<Object>) rpcResponse.result());
                    taskAdapter = taskAdapter.withMessage(message, statusMessage).asCompleted();
                }
            }

            return Optional.of(taskAdapter);
        });
    }

    @Override
    public void requestTaskCancellation(TaskContextId taskContextId, String taskId, Optional<String> statusMessage)
    {
        SessionId sessionId = toSessionId(taskContextId);
        SessionValueKey<TaskAdapter> taskAdapterKey = taskAdapterKey(taskId);

        sessionController.computeSessionValue(sessionId, taskAdapterKey, current -> {
            TaskAdapter taskAdapter = validateModifyTask(taskContextId, taskId, current);
            return Optional.of(taskAdapter.withCancellationRequested(statusMessage));
        });
    }

    @Override
    public void completeTask(TaskContextId taskContextId, String taskId, JsonRpcResponse<?> response, Optional<String> statusMessage)
    {
        SessionId sessionId = toSessionId(taskContextId);
        SessionValueKey<TaskAdapter> taskAdapterKey = taskAdapterKey(taskId);

        sessionController.computeSessionValue(sessionId, taskAdapterKey, current -> {
            TaskAdapter taskAdapter = validateTask(taskContextId, taskId, current);
            validateTaskNotCompleted(taskContextId, taskId, taskAdapter);

            taskAdapter = taskAdapter.withMessage(response, statusMessage).asCompleted();

            return Optional.of(taskAdapter);
        });
    }

    @Override
    public void addServerToClientResponse(TaskContextId taskContextId, String taskId, JsonRpcResponse<?> response)
    {
        SessionId sessionId = toSessionId(taskContextId);
        SessionValueKey<TaskAdapter> taskAdapterKey = taskAdapterKey(taskId);

        sessionController.computeSessionValue(sessionId, taskAdapterKey, current -> {
            TaskAdapter taskAdapter = validateTask(taskContextId, taskId, current);
            validateTaskNotCompleted(taskContextId, taskId, taskAdapter);

            taskAdapter = taskAdapter.withResponse(response);

            return Optional.of(taskAdapter);
        });
    }

    @Override
    public <T> T executeCancellable(TaskContextId taskContextId, String taskId, Supplier<T> supplier)
    {
        return cancellationController.builder(toSessionId(taskContextId), taskAdapterKey(taskId))
                .withTaskId(taskId)
                .withIsCancelledCondition(cancellationRequested)
                .withPostCancellationAction((sessionId, key) -> sessionController.computeSessionValue(sessionId, key, current -> {
                    TaskAdapter taskAdapter = validateTask(taskContextId, taskId, current);
                    validateTaskNotCompleted(taskContextId, taskId, taskAdapter);

                    JsonRpcErrorDetail error = new JsonRpcErrorDetail(INVALID_REQUEST, "Task was cancelled");
                    JsonRpcResponse<?> response = new JsonRpcResponse<>(taskAdapter.requestId(), Optional.of(error), Optional.empty());
                    taskAdapter = taskAdapter.withMessage(response, Optional.of("Task was cancelled")).asCompleted();
                    return Optional.of(taskAdapter);
                }))
                .executeCancellable(supplier);
    }

    @Override
    public TaskAdapter blockUntilCondition(TaskContextId taskContextId, String taskId, Duration timeout, Duration pollInterval, TaskCondition condition)
            throws InterruptedException, TimeoutException
    {
        SessionId sessionId = toSessionId(taskContextId);
        SessionValueKey<TaskAdapter> taskAdapterKey = taskAdapterKey(taskId);

        long remainingMs = timeout.toMillis();

        while (remainingMs > 0) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            BlockingResult<TaskAdapter> blockResult = sessionController.blockUntil(sessionId, taskAdapterKey, pollInterval, maybeTask -> maybeTask.map(condition::test).orElse(false));
            if (blockResult instanceof Fulfilled<TaskAdapter>(var taskAdapter)) {
                return taskAdapter;
            }

            remainingMs -= stopwatch.elapsed().toMillis();
        }

        throw new TimeoutException("Timeout waiting for task response: " + taskId);
    }

    private static TaskAdapter validateModifyTask(TaskContextId taskContextId, String taskId, Optional<TaskAdapter> current)
    {
        TaskAdapter taskAdapter = validateTask(taskContextId, taskId, current);

        if (taskAdapter.cancellationRequested()) {
            throw exception(INVALID_PARAMS, "Task has been cancelled. TaskContextId: %s, TaskId: %s ".formatted(taskContextId, taskId));
        }

        validateTaskNotCompleted(taskContextId, taskId, taskAdapter);

        return taskAdapter;
    }

    private static void validateTaskNotCompleted(TaskContextId taskContextId, String taskId, TaskAdapter taskAdapter)
    {
        if (taskAdapter.completedAt().isPresent()) {
            throw exception(INVALID_PARAMS, "Cannot set message on completed task. TaskContextId: %s, TaskId: %s ".formatted(taskContextId, taskId));
        }
    }

    private static TaskAdapter validateTask(TaskContextId taskContextId, String taskId, Optional<TaskAdapter> current)
    {
        return current.orElseThrow(() -> exception(INVALID_PARAMS, "Task does not exist. TaskContextId: %s, TaskId: %s ".formatted(taskContextId, taskId)));
    }

    private static SessionId toSessionId(TaskContextId taskContextId)
    {
        return new SessionId(taskContextId.id());
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

    private void cleanExpiredTasks(TaskContextId taskContextId)
    {
        Optional<String> cursor = Optional.empty();
        do {
            List<Map.Entry<String, TaskAdapter>> entries = sessionController.listSessionValues(toSessionId(taskContextId), TaskAdapter.class, CLEANUP_PAGE_SIZE, cursor);

            entries.forEach(entry -> {
                String taskId = entry.getKey();
                TaskAdapter task = entry.getValue();
                int ttlMs = task.ttlMs();

                if (task.completedAt().isPresent()) {
                    Duration timeSinceCompletion = Duration.between(task.completedAt().get(), Instant.now());
                    if (timeSinceCompletion.toMillis() > ttlMs) {
                        log.info("Cleaning up completed task. TaskContextId: %s, TaskId: %s".formatted(taskContextId, taskId));
                        deleteTask(taskContextId, taskId);
                    }
                }
                else {
                    Duration timeSinceLastUpdate = Duration.between(task.lastUpdatedAt(), Instant.now());
                    if (timeSinceLastUpdate.compareTo(abandonedTaskThreshold) > 0) {
                        log.info("Cleaning up abandoned task. TaskContextId: %s, TaskId: %s".formatted(taskContextId, taskId));
                        deleteTask(taskContextId, taskId);
                    }
                }
            });

            cursor = (entries.size() < CLEANUP_PAGE_SIZE) ? Optional.empty() : Optional.of(entries.getLast().getKey());
        } while (cursor.isPresent());
    }
}
