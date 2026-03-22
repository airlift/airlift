package io.airlift.mcp.legacy;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.airlift.log.Logger;
import io.airlift.mcp.McpConfig;
import io.airlift.mcp.legacy.sessions.LegacyBlockingResult.EmptyFulfilled;
import io.airlift.mcp.legacy.sessions.LegacyBlockingResult.Fulfilled;
import io.airlift.mcp.legacy.sessions.LegacyBlockingResult.TimedOut;
import io.airlift.mcp.legacy.sessions.LegacySession;
import io.airlift.mcp.legacy.sessions.LegacySessionValueKey;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.util.concurrent.MoreExecutors.shutdownAndAwaitTermination;
import static io.airlift.concurrent.Threads.virtualThreadsNamed;
import static io.airlift.mcp.McpException.exception;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newThreadPerTaskExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;

@VisibleForTesting
public class LegacyCancellationController
{
    private static final Logger log = Logger.get(LegacyCancellationController.class);

    private final Set<Object> activeRequestIds = Sets.newConcurrentHashSet();
    private final Duration interval;
    private final ExecutorService executorService;

    @Inject
    public LegacyCancellationController(McpConfig mcpConfig)
    {
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

    public <T, R> Builder<T, R> builder(LegacySession session, LegacySessionValueKey<T> key)
    {
        return new Builder<>(session, key);
    }

    public class Builder<T, R>
    {
        private final LegacySession session;
        private final LegacySessionValueKey<T> key;

        private Object requestId;
        private Predicate<Optional<T>> condition;
        private BiConsumer<LegacySession, LegacySessionValueKey<T>> postCancellationAction = (_, _) -> {};
        private Function<Optional<T>, Optional<String>> reasonMapper = _ -> Optional.empty();

        private Builder(LegacySession session, LegacySessionValueKey<T> key)
        {
            this.session = requireNonNull(session, "session is null");
            this.key = requireNonNull(key, "key is null");
        }

        public Builder<T, R> withRequestId(Object requestId)
        {
            this.requestId = requireNonNull(requestId, "requestId is null");
            return this;
        }

        public Builder<T, R> withIsCancelledCondition(Predicate<Optional<T>> condition)
        {
            this.condition = requireNonNull(condition, "condition is null");
            return this;
        }

        public Builder<T, R> withPostCancellationAction(BiConsumer<LegacySession, LegacySessionValueKey<T>> postCancellationAction)
        {
            this.postCancellationAction = requireNonNull(postCancellationAction, "postCancellationAction is null");
            return this;
        }

        public Builder<T, R> withReasonMapper(Function<Optional<T>, Optional<String>> reasonMapper)
        {
            this.reasonMapper = requireNonNull(reasonMapper, "reasonMapper is null");
            return this;
        }

        public R executeCancellable(Supplier<R> supplier)
        {
            requireNonNull(requestId, "requestId is required");
            requireNonNull(condition, "condition is required");

            return LegacyCancellationController.this.executeCancellable(this, supplier);
        }
    }

    private <T, R> R executeCancellable(Builder<T, R> builder, Supplier<R> supplier)
    {
        if (!activeRequestIds.add(builder.requestId)) {
            throw exception("Request is already being processed: " + builder.requestId);
        }

        Runnable cancellationThreadCloser = () -> {};
        try {
            cancellationThreadCloser = startCancellationThread(builder);
            return supplier.get();
        }
        finally {
            activeRequestIds.remove(builder.requestId);
            cancellationThreadCloser.run();
        }
    }

    private <T, R> Runnable startCancellationThread(Builder<T, R> builder)
    {
        Thread activeThread = Thread.currentThread();

        AtomicBoolean isClosed = new AtomicBoolean();

        executorService.execute(() -> {
            try {
                while (!isClosed.get()) {
                    switch (builder.session.blockUntil(builder.key, interval, builder.condition)) {
                        case Fulfilled<T>(var value) -> handleCancellation(builder, Optional.of(value), isClosed, activeThread);
                        case EmptyFulfilled _ -> handleCancellation(builder, Optional.empty(), isClosed, activeThread);
                        case TimedOut _ -> {}   // do nothing and iterate again
                    }
                }
            }
            catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                // ignore
            }
        });

        /*
            A naive implementation would be to interrupt the cancellation thread. However, this might cause
            the current thread to be interrupted when the "builder.postCancellationAction" is called causing that
            action to fail. Instead, use a simple boolean. It may mean that this thread lives a bit longer than necessary,
            but it avoids potential issues with interrupting the wrong thread.
         */
        return () -> isClosed.set(true);
    }

    private <T, R> void handleCancellation(Builder<T, R> builder, Optional<T> value, AtomicBoolean isClosed, Thread activeThread)
    {
        isClosed.set(true);

        try {
            if (activeRequestIds.contains(builder.requestId)) {
                Optional<String> maybeReason = builder.reasonMapper.apply(value);
                log.info("Cancelling request %s. Reason: %s".formatted(builder.requestId, maybeReason.orElse("No reason provided")));
                activeThread.interrupt();
            }
        }
        finally {
            builder.postCancellationAction.accept(builder.session, builder.key);
        }
    }
}
