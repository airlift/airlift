package io.airlift.mcp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import io.airlift.mcp.tasks.TaskAdapter;
import io.airlift.mcp.tasks.TaskContextId;
import io.airlift.mcp.tasks.TaskController;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.sessions.SessionValueKey.cancellationKey;
import static io.airlift.mcp.sessions.SessionValueKey.taskAdapterKey;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

@VisibleForTesting
public class CancellationController
{
    private final McpCancellationHandler cancellationHandler;
    private final Optional<SessionController> sessionController;
    private final Set<Object> activeRequestIds = Sets.newConcurrentHashSet();
    private final Set<String> activeTaskIds = Sets.newConcurrentHashSet();
    private final Duration interval;
    private final TaskController taskController;

    @Inject
    public CancellationController(McpCancellationHandler cancellationHandler, Optional<SessionController> sessionController, TaskController taskController, McpConfig mcpConfig)
    {
        this.cancellationHandler = requireNonNull(cancellationHandler, "cancellationHandler is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        this.taskController = requireNonNull(taskController, "taskController is null");

        interval = mcpConfig.getCancellationCheckInterval().toJavaTime();
    }

    @VisibleForTesting
    public Collection<Object> activeRequestIds()
    {
        return activeRequestIds;
    }

    public Object executeCancellableRequest(Optional<SessionId> maybeSessionId, Object requestId, Supplier<Object> supplier)
    {
        return maybeSessionId.map(sessionId -> {
            if (!activeRequestIds.add(requestId)) {
                throw exception("Request is already being processed: " + requestId);
            }

            Future<?> future = sessionController.map(controller -> cancelRequestThread(controller, Thread.currentThread(), sessionId, requestId))
                    .orElseGet(Futures::immediateCancelledFuture);
            try {
                return supplier.get();
            }
            finally {
                activeRequestIds.remove(requestId);
                future.cancel(true);
            }
        }).orElseGet(supplier);
    }

    public Object executeCancellableTask(TaskContextId taskContextId, String taskId, Supplier<Object> supplier)
    {
        if (!activeTaskIds.add(taskId)) {
            throw exception("Task is already being processed: " + taskId);
        }

        Future<?> future = sessionController.map(controller -> cancelTaskThread(controller, Thread.currentThread(), taskContextId, taskId))
                .orElseGet(Futures::immediateCancelledFuture);
        try {
            return supplier.get();
        }
        finally {
            activeTaskIds.remove(taskId);
            future.cancel(true);
        }
    }

    @SuppressWarnings({"BusyWait", "resource"})
    private Future<?> cancelTaskThread(SessionController sessionController, Thread activeThread, TaskContextId taskContextId, String taskId)
    {
        SessionValueKey<TaskAdapter> taskAdapterKey = taskAdapterKey(taskId);
        SessionId sessionId = taskController.toSessionId(taskContextId);

        // TODO - replace with virtual thread when we've moved to JDK 24+
        return newSingleThreadExecutor(daemonThreadsNamed("cancellation-controller-task")).submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (sessionController.blockUntilCondition(sessionId, taskAdapterKey, interval, Optional::isPresent).isEmpty()) {
                        // blockUntilCondition not supported, so just sleep
                        Thread.sleep(interval.toMillis());
                    }

                    Optional<TaskAdapter> maybeTask = sessionController.getSessionValue(sessionId, taskAdapterKey);
                    if (maybeTask.map(TaskAdapter::cancellationRequested).orElse(false)) {
                        try {
                            if (activeTaskIds.contains(taskId)) {
                                cancellationHandler.cancelRequest(activeThread, taskId, Optional.empty());
                            }
                        }
                        finally {
                            sessionController.deleteSessionValue(sessionId, taskAdapterKey);
                        }
                        break;
                    }
                }
            }
            catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                // ignore
            }
        });
    }

    @SuppressWarnings({"resource", "BusyWait"})
    private Future<?> cancelRequestThread(SessionController sessionController, Thread activeThread, SessionId sessionId, Object requestId)
    {
        SessionValueKey<CancelledNotification> cancellationKey = cancellationKey(requestId);

        // TODO - replace with virtual thread when we've moved to JDK 24+
        return newSingleThreadExecutor(daemonThreadsNamed("cancellation-controller-request")).submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (sessionController.blockUntilCondition(sessionId, cancellationKey, interval, Optional::isPresent).isEmpty()) {
                        // blockUntilCondition not supported, so just sleep
                        Thread.sleep(interval.toMillis());
                    }

                    Optional<CancelledNotification> maybeCancellation = sessionController.getSessionValue(sessionId, cancellationKey);
                    if (maybeCancellation.isPresent()) {
                        if (activeRequestIds.contains(requestId)) {
                            cancellationHandler.cancelRequest(activeThread, requestId, maybeCancellation.get().reason());
                        }
                        break;
                    }
                }
            }
            catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                // ignore
            }
        });
    }
}
