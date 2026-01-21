package io.airlift.http.client;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import static java.util.Objects.requireNonNull;

public final class RequestLifecycleHandler
{
    public static RequestLifecycleHandler create()
    {
        return new RequestLifecycleHandler();
    }

    private SettableFuture<?> cancelled;

    private RequestLifecycleHandler()
    {
        this.cancelled = SettableFuture.create();
    }

    public void cancel()
    {
        requireNonNull(cancelled, "handler is destroyed").set(null);
    }

    public void destroy()
    {
        // Prevent reuse of the lifecycle handler. Allowing reuse would require resetting listeners on the cancelled future,
        // or otherwise we would get a memory leak.
        cancelled = null;
    }

    /**
     * Returns the cancellation future. Typically used by the HTTP client implementation.
     */
    public ListenableFuture<?> cancelled()
    {
        return requireNonNull(cancelled, "handler is destroyed");
    }
}
