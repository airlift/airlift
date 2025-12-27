package io.airlift.mcp.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpCancellationHandler;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.ValueKey;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.sessions.ValueKey.cancellationKey;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

@VisibleForTesting
public class CancellationController
{
    private static final Logger log = Logger.get(CancellationController.class);

    private final McpCancellationHandler cancellationHandler;
    private final Optional<SessionController> sessionController;
    private final Optional<ScheduledExecutorService> executorService;
    private final Map<Object, ActiveThread> activeThreads = new ConcurrentHashMap<>();

    private record ActiveThread(SessionId sessionId, Thread thread)
    {
        private ActiveThread
        {
            requireNonNull(sessionId, "sessionId is null");
            requireNonNull(thread, "thread is null");
        }
    }

    @Inject
    CancellationController(McpCancellationHandler cancellationHandler, Optional<SessionController> sessionController)
    {
        this.cancellationHandler = requireNonNull(cancellationHandler, "cancellationHandler is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        executorService = sessionController.map(_ -> newSingleThreadScheduledExecutor(daemonThreadsNamed("mcp-cancellation-controller")));
    }

    @PostConstruct
    public void start()
    {
        sessionController.ifPresent(controller ->
                executorService.ifPresent(executor -> executor.scheduleWithFixedDelay(() -> checkCancellations(controller), 1, 1, SECONDS)));
    }

    @PreDestroy
    public void shutdown()
    {
        executorService.ifPresent(executor -> {
            if (!shutdownAndAwaitTermination(executor, 10, SECONDS)) {
                log.warn("Executor shutdown failed");
            }
        });
    }

    @VisibleForTesting
    public Collection<Object> activeRequestIds()
    {
        return activeThreads.keySet();
    }

    Object execute(SessionId sessionId, Object requestId, Supplier<Object> supplier)
    {
        if (activeThreads.putIfAbsent(requestId, new ActiveThread(sessionId, Thread.currentThread())) != null) {
            throw exception("Request is already being processed: " + requestId);
        }
        try {
            return supplier.get();
        }
        finally {
            activeThreads.remove(requestId);
        }
    }

    private void checkCancellations(SessionController sessionController)
    {
        activeThreads.forEach((requestId, activeThread) -> {
            ValueKey<CancelledNotification> cancellationKey = cancellationKey(requestId);
            sessionController.getSessionValue(activeThread.sessionId, cancellationKey).ifPresent(cancellation -> {
                try {
                    cancellationHandler.cancelRequest(activeThread.thread, requestId, cancellation.reason());
                }
                finally {
                    sessionController.deleteSessionValue(activeThread.sessionId, cancellationKey);
                }
            });
        });
    }
}
