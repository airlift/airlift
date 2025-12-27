package io.airlift.mcp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.sessions.SessionController;
import io.airlift.mcp.sessions.SessionId;
import io.airlift.mcp.sessions.SessionValueKey;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.concurrent.Threads.virtualThreadsNamed;
import static io.airlift.mcp.McpException.exception;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

@VisibleForTesting
public class CancellationController
{
    private static final Logger log = Logger.get(CancellationController.class);

    private final McpCancellationHandler cancellationHandler;
    private final Optional<SessionController> sessionController;
    private final Set<Object> activeRequestIds = Sets.newConcurrentHashSet();
    private final Duration interval;
    private final ExecutorService executorService;

    @Inject
    public CancellationController(McpCancellationHandler cancellationHandler, Optional<SessionController> sessionController, McpConfig mcpConfig)
    {
        this.cancellationHandler = requireNonNull(cancellationHandler, "cancellationHandler is null");
        this.sessionController = requireNonNull(sessionController, "sessionController is null");

        executorService = newThreadPerTaskExecutor(virtualThreadsNamed("CancellationController-%s"));

        interval = mcpConfig.getCancellationCheckInterval().toJavaTime();
    }

    @VisibleForTesting
    public Collection<Object> activeRequestIds()
    {
        return activeRequestIds;
    }

    @PreDestroy
    public void shutdown()
    {
        if (!shutdownAndAwaitTermination(executorService, 30, SECONDS)) {
            log.warn("Cancellation controller executor did not terminate");
        }
    }

    public <T> Builder<T> builder(SessionId sessionId, SessionValueKey<T> key)
    {
        SessionController localSessionController = sessionController
                .orElseThrow(() -> exception("SessionController is required for cancellations"));
        return new Builder<>(localSessionController, sessionId, key);
    }

    public class Builder<T>
    {
        private final SessionController sessionController;
        private final SessionId sessionId;
        private final SessionValueKey<T> key;

        private Object requestId;
        private Function<Optional<T>, Boolean> condition;
        private BiConsumer<SessionId, SessionValueKey<T>> postCancellationAction = (_, _) -> {};
        private Function<Optional<T>, Optional<String>> reasonMapper = _ -> Optional.empty();

        private Builder(SessionController sessionController, SessionId sessionId, SessionValueKey<T> key)
        {
            this.sessionController = requireNonNull(sessionController, "sessionController is null");
            this.sessionId = requireNonNull(sessionId, "sessionId is null");
            this.key = requireNonNull(key, "key is null");
        }

        public Builder<T> withRequestId(Object requestId)
        {
            this.requestId = requireNonNull(requestId, "requestId is null");
            return this;
        }

        public Builder<T> withIsCancelledCondition(Function<Optional<T>, Boolean> condition)
        {
            this.condition = requireNonNull(condition, "condition is null");
            return this;
        }

        public Builder<T> withPostCancellationAction(BiConsumer<SessionId, SessionValueKey<T>> postCancellationAction)
        {
            this.postCancellationAction = requireNonNull(postCancellationAction, "postCancellationAction is null");
            return this;
        }

        public Builder<T> withReasonMapper(Function<Optional<T>, Optional<String>> reasonMapper)
        {
            this.reasonMapper = requireNonNull(reasonMapper, "reasonMapper is null");
            return this;
        }

        public Object executeCancellable(Supplier<Object> supplier)
        {
            requireNonNull(requestId, "requestId is required");
            requireNonNull(condition, "condition is required");

            return CancellationController.this.executeCancellable(this, supplier);
        }
    }

    private <T> Object executeCancellable(Builder<T> builder, Supplier<Object> supplier)
    {
        if (!activeRequestIds.add(builder.requestId)) {
            throw exception("Request is already being processed: " + builder.requestId);
        }

        Future<?> future = null;
        try {
            future = startCancellationThread(builder);
            return supplier.get();
        }
        finally {
            activeRequestIds.remove(builder.requestId);
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    private <T> Future<?> startCancellationThread(Builder<T> builder)
    {
        Thread activeThread = Thread.currentThread();

        return executorService.submit(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    builder.sessionController.blockUntilCondition(builder.sessionId, builder.key, interval, builder.condition);
                    Optional<T> value = builder.sessionController.getSessionValue(builder.sessionId, builder.key);
                    if (builder.condition.apply(value)) {
                        try {
                            if (activeRequestIds.contains(builder.requestId)) {
                                Optional<String> maybeReason = builder.reasonMapper.apply(value);
                                cancellationHandler.cancelRequest(activeThread, builder.requestId, maybeReason);
                            }
                        }
                        finally {
                            builder.postCancellationAction.accept(builder.sessionId, builder.key);
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
