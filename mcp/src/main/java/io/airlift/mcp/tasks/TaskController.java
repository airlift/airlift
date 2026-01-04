package io.airlift.mcp.tasks;

import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.CancellationController;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.McpIdentity;
import io.airlift.mcp.McpRequestContext;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.airlift.mcp.sessions.SessionController.FOREVER_TTL;
import static io.airlift.mcp.tasks.Tasks.deleteTask;
import static java.lang.Math.toIntExact;
import static java.util.Objects.requireNonNull;

public class TaskController
{
    private static final Logger log = Logger.get(TaskController.class);

    private static final int CLEANUP_PAGE_SIZE = 100;

    private final SessionController sessionController;
    private final CancellationController cancellationController;
    private final int defaultTaskTtlMs;
    private final int defaultPingThresholdMs;
    private final Duration abandonedTaskThreshold;

    @Inject
    public TaskController(SessionController sessionController, CancellationController cancellationController, McpConfig mcpConfig)
    {
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.cancellationController = requireNonNull(cancellationController, "cancellationController is null");

        defaultTaskTtlMs = toIntExact(mcpConfig.getDefaultTaskTtl().toMillis());
        defaultPingThresholdMs = toIntExact(mcpConfig.getEventStreamingPingThreshold().toMillis());
        abandonedTaskThreshold = mcpConfig.getAbandonedTaskThreshold().toJavaTime();
    }

    public TaskContextId createTaskContext(McpIdentity identity)
    {
        return new TaskContextIdImpl(sessionController.createSession(identity, FOREVER_TTL).id());
    }

    public boolean validateTaskContext(TaskContextId taskContextId)
    {
        if (!sessionController.validateSession(toSessionId(taskContextId))) {
            return false;
        }

        cleanExpiredTasks(taskContextId);

        return true;
    }

    public void deleteTaskContext(TaskContextId taskContextId)
    {
        sessionController.deleteSession(toSessionId(taskContextId));
    }

    /**
     * Return the tasks instance for the given context outside a request. You will not be able
     * to create tasks with this instance.
     */
    public Tasks tasks(TaskContextId taskContextId)
    {
        return new Tasks(sessionController, cancellationController, taskContextId, Optional.empty(), Optional.empty(), defaultTaskTtlMs, defaultPingThresholdMs);
    }

    /**
     * Return the tasks instance for the given context and request. Normally, use
     * {@link McpRequestContext#tasks()}
     */
    public Tasks tasksForRequest(TaskContextId taskContextId, Object requestId, Optional<Object> progressToken)
    {
        return new Tasks(sessionController, cancellationController, taskContextId, Optional.of(requestId), progressToken, defaultTaskTtlMs, defaultPingThresholdMs);
    }

    static SessionId toSessionId(TaskContextId taskContextId)
    {
        return new SessionId(taskContextId.id());
    }

    private void cleanExpiredTasks(TaskContextId taskContextId)
    {
        Optional<String> cursor = Optional.empty();
        do {
            List<Map.Entry<String, TaskFacade>> entries = sessionController.listSessionValues(toSessionId(taskContextId), TaskFacade.class, CLEANUP_PAGE_SIZE, cursor);

            entries.forEach(entry -> {
                String taskId = entry.getKey();
                TaskFacade task = entry.getValue();
                int ttlMs = task.ttlMs();

                if (task.completedAt().isPresent()) {
                    Duration timeSinceCompletion = Duration.between(task.completedAt().get(), Instant.now());
                    if (timeSinceCompletion.toMillis() > ttlMs) {
                        log.info("Cleaning up completed task. TaskContextId: %s, TaskId: %s".formatted(taskContextId, taskId));
                        deleteTask(sessionController, taskContextId, taskId);
                    }
                }
                else {
                    Duration timeSinceLastUpdate = Duration.between(task.lastUpdatedAt(), Instant.now());
                    if (timeSinceLastUpdate.compareTo(abandonedTaskThreshold) > 0) {
                        log.info("Cleaning up abandoned task. TaskContextId: %s, TaskId: %s".formatted(taskContextId, taskId));
                        deleteTask(sessionController, taskContextId, taskId);
                    }
                }
            });

            cursor = (entries.size() < CLEANUP_PAGE_SIZE) ? Optional.empty() : Optional.of(entries.getLast().getKey());
        } while (cursor.isPresent());
    }
}
