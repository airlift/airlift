package io.airlift.api;

import java.util.concurrent.Executor;

/**
 * Request-scoped cancellation token for APIs that need to clean up work when the request cannot complete normally.
 */
public interface ApiCancellation
{
    boolean isCancelled();

    /**
     * Registers a listener to run once on the supplied executor when the request is cancelled.
     */
    ListenerRegistration onCancel(Executor executor, Runnable listener);

    interface ListenerRegistration
            extends AutoCloseable
    {
        /**
         * Removes the listener if cancellation has not already started.
         */
        @Override
        void close();
    }
}
