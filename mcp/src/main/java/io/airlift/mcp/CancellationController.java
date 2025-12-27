package io.airlift.mcp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.model.CancelledNotification;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.sessions.SessionValueKey.cancellationKey;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@VisibleForTesting
public class CancellationController
{
    private static final Logger log = Logger.get(CancellationController.class);

    private final McpCancellationHandler cancellationHandler;
    private final Optional<SessionController> sessionController;
    private final Optional<ScheduledExecutorService> executorService;
    private final Map<Object, ActiveThread> activeThreads = new ConcurrentHashMap<>();
    private final Duration interval;

    private record ActiveThread(SessionId sessionId, Thread thread)
    {
        private ActiveThread
        {
            requireNonNull(sessionId, "sessionId is null");
            requireNonNull(thread, "thread is null");
        }
    }

    @Inject
    public CancellationController(McpCancellationHandler cancellationHandler, Optional<SessionController> sessionController, McpConfig mcpConfig)
    {
        this.cancellationHandler = requireNonNull(cancellationHandler, "cancellationHandler is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");
        executorService = sessionController.map(_ -> newSingleThreadScheduledExecutor(daemonThreadsNamed("mcp-cancellation-controller")));

        interval = mcpConfig.getCancellationCheckInterval().toJavaTime();
    }

    @PostConstruct
    public void start()
    {
        sessionController.ifPresent(controller ->
                executorService.ifPresent(executor -> executor.scheduleWithFixedDelay(() -> checkCancellations(controller), interval.toMillis(), interval.toMillis(), MILLISECONDS)));
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

    public Object executeCancellable(Optional<SessionId> maybeSessionId, Object requestId, Supplier<Object> supplier)
    {
        return maybeSessionId.map(sessionId -> {
            if (activeThreads.putIfAbsent(requestId, new ActiveThread(sessionId, Thread.currentThread())) != null) {
                throw exception("Request is already being processed: " + requestId);
            }
            try {
                return supplier.get();
            }
            finally {
                activeThreads.remove(requestId);
            }
        }).orElseGet(supplier);
    }

    private void checkCancellations(SessionController sessionController)
    {
        activeThreads.forEach((requestId, activeThread) -> {
            SessionValueKey<CancelledNotification> cancellationKey = cancellationKey(requestId);
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
