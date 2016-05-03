package io.airlift.concurrent;

import com.google.common.util.concurrent.ForwardingBlockingQueue;
import io.airlift.units.Duration;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.IntSupplier;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Factory for creating a cached thread pool with a hard bound on the number of
 * created threads. This allows configuring thread pools with a large upper
 * bound without unnecessarily creating many extra threads when usage is light.
 * <p>
 * The returned thread pool is a {@link ThreadPoolExecutor} that queues at most
 * one task per idle thread before creating a new thread.
 */
public final class BoundedThreadPool
{
    private static final Duration DEFAULT_KEEP_ALIVE = new Duration(60, SECONDS);

    private BoundedThreadPool() {}

    public static ThreadPoolExecutor newBoundedThreadPool(int maxThreads, ThreadFactory threadFactory)
    {
        return newBoundedThreadPool(0, maxThreads, threadFactory);
    }

    public static ThreadPoolExecutor newBoundedThreadPool(int minThreads, int maxThreads, ThreadFactory threadFactory)
    {
        return newBoundedThreadPool(minThreads, maxThreads, threadFactory, DEFAULT_KEEP_ALIVE);
    }

    public static ThreadPoolExecutor newBoundedThreadPool(int minThreads, int maxThreads, ThreadFactory threadFactory, Duration keepAlive)
    {
        RefusingQueue<Runnable> queue = new RefusingQueue<>();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                minThreads, maxThreads,
                keepAlive.toMillis(), MILLISECONDS,
                queue,
                threadFactory,
                (runnable, executor) -> queue.add(runnable));
        queue.setSizeSupplier(() -> pool.getPoolSize() - pool.getActiveCount());
        return pool;
    }

    private static class RefusingQueue<T>
            extends ForwardingBlockingQueue<T>
    {
        private final BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        private volatile IntSupplier sizeSupplier = () -> 0;

        public void setSizeSupplier(IntSupplier supplier)
        {
            sizeSupplier = requireNonNull(supplier, "supplier is null");
        }

        @Override
        protected BlockingQueue<T> delegate()
        {
            return queue;
        }

        @Override
        public boolean offer(T o)
        {
            if (queue.size() < sizeSupplier.getAsInt()) {
                return queue.offer(o);
            }
            return false;
        }
    }
}
