package io.airlift.concurrent;

import com.google.common.base.Preconditions;
import io.airlift.log.Logger;

import javax.annotation.concurrent.ThreadSafe;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkState;

/**
 * Guarantees that no more than maxThreads will be used to execute tasks submitted
 * through {@link #execute(Runnable) execute()}.
 * <p>
 * There are a few interesting properties:
 * <ul>
 * <li>Multiple BoundedExecutors over a single coreExecutor will have fair sharing
 * of the coreExecutor threads proportional to their relative maxThread counts, but
 * can use less if not as active.</li>
 * <li>Tasks submitted to a BoundedExecutor is guaranteed to have those tasks
 * handed to threads in that order.</li>
 * <li>Will not encounter starvation</li>
 * </ul>
 */
@ThreadSafe
public class BoundedExecutor
        implements Executor
{
    private static final Logger log = Logger.get(BoundedExecutor.class);

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final AtomicBoolean failed = new AtomicBoolean();

    private final Executor coreExecutor;
    private final int maxThreads;

    public BoundedExecutor(Executor coreExecutor, int maxThreads)
    {
        Preconditions.checkNotNull(coreExecutor, "coreExecutor is null");
        Preconditions.checkArgument(maxThreads > 0, "maxThreads must be greater than zero");
        this.coreExecutor = coreExecutor;
        this.maxThreads = maxThreads;
    }

    @Override
    public void execute(Runnable task)
    {
        checkState(!failed.get(), "BoundedExecutor is in a failed state");

        queue.add(task);

        int size = queueSize.incrementAndGet();
        if (size <= maxThreads) {
            // If able to grab a permit (aka size <= maxThreads), then we are short exactly one draining thread
            try {
                coreExecutor.execute(this::drainQueue);
            }
            catch (Throwable e) {
                failed.set(true);
                log.error("BoundedExecutor state corrupted due to underlying executor failure");
                throw e;
            }
        }
    }

    private void drainQueue()
    {
        // INVARIANT: queue has at least one task available when this method is called
        do {
            try {
                queue.poll().run();
            }
            catch (Throwable e) {
                log.error(e, "Task failed");
            }
        }
        while (queueSize.getAndDecrement() > maxThreads);
    }
}
