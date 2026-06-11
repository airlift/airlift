package io.airlift.api.binding;

import io.airlift.api.ApiCancellation;
import io.airlift.log.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

class RequestApiCancellation
        implements ApiCancellation
{
    private static final Logger log = Logger.get(RequestApiCancellation.class);
    private static final ListenerRegistration NOOP_REGISTRATION = () -> {};

    private final CompletableFuture<?> cancellation;

    RequestApiCancellation(CompletableFuture<?> cancellation)
    {
        this.cancellation = requireNonNull(cancellation, "cancellation is null");
    }

    static ApiCancellation empty()
    {
        return new RequestApiCancellation(CompletableFuture.completedFuture(null));
    }

    @Override
    public boolean isCancelled()
    {
        return cancellation.isCancelled();
    }

    @Override
    public ListenerRegistration onCancel(Executor executor, Runnable listener)
    {
        requireNonNull(executor, "executor is null");
        requireNonNull(listener, "listener is null");

        if (cancellation.isDone() && !cancellation.isCancelled()) {
            return NOOP_REGISTRATION;
        }

        CompletableFuture<?> registration = cancellation.whenComplete((_, _) -> {
            if (!cancellation.isCancelled()) {
                return;
            }

            try {
                executor.execute(() -> {
                    try {
                        listener.run();
                    }
                    catch (Throwable e) {
                        log.warn(e, "API cancellation listener failed");
                    }
                });
            }
            catch (RuntimeException e) {
                log.warn(e, "API cancellation listener executor rejected listener");
            }
        });

        return () -> {
            if (!cancellation.isDone()) {
                registration.cancel(false);
            }
        };
    }
}
