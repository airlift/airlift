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

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static io.airlift.mcp.McpException.exception;
import static io.airlift.mcp.sessions.SessionValueKey.cancellationKey;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

@VisibleForTesting
public class CancellationController
{
    private final McpCancellationHandler cancellationHandler;
    private final Optional<SessionController> sessionController;
    private final Set<Object> activeRequestIds = Sets.newConcurrentHashSet();
    private final Duration interval;

    @Inject
    public CancellationController(McpCancellationHandler cancellationHandler, Optional<SessionController> sessionController, McpConfig mcpConfig)
    {
        this.cancellationHandler = requireNonNull(cancellationHandler, "cancellationHandler is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");

        interval = mcpConfig.getCancellationCheckInterval().toJavaTime();
    }

    @VisibleForTesting
    public Collection<Object> activeRequestIds()
    {
        return activeRequestIds;
    }

    public Object executeCancellable(Optional<SessionId> maybeSessionId, Object requestId, Supplier<Object> supplier)
    {
        return maybeSessionId.map(sessionId -> {
            if (!activeRequestIds.add(requestId)) {
                throw exception("Request is already being processed: " + requestId);
            }

            Future<?> future = sessionController.map(controller -> cancelThread(controller, Thread.currentThread(), sessionId, requestId))
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

    @SuppressWarnings({"resource", "BusyWait"})
    private Future<?> cancelThread(SessionController sessionController, Thread activeThread, SessionId sessionId, Object requestId)
    {
        SessionValueKey<CancelledNotification> cancellationKey = cancellationKey(requestId);

        // TODO - replace with virtual thread when we've moved to JDK 24+
        return newSingleThreadExecutor(daemonThreadsNamed("cancellation-controller")).submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    if (sessionController.blockUntilCondition(sessionId, cancellationKey, interval, Optional::isPresent).isEmpty()) {
                        // blockUntilCondition not supported, so just sleep
                        Thread.sleep(interval.toMillis());
                    }

                    Optional<CancelledNotification> maybeCancellation = sessionController.getSessionValue(sessionId, cancellationKey);
                    if (maybeCancellation.isPresent()) {
                        try {
                            if (activeRequestIds.contains(requestId)) {
                                cancellationHandler.cancelRequest(activeThread, requestId, maybeCancellation.get().reason());
                            }
                        }
                        finally {
                            sessionController.deleteSessionValue(sessionId, cancellationKey);
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
