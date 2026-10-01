package io.airlift.concurrent;

import com.google.errorprone.annotations.ThreadSafe;
import io.airlift.log.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

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
    private final Runnable drainQueueTask = this::drainQueue;

    private final Executor coreExecutor;
    private final int maxThreads;

    public BoundedExecutor(Executor coreExecutor, int maxThreads)
    {
        requireNonNull(coreExecutor, "coreExecutor is null");
        checkArgument(maxThreads > 0, "maxThreads must be greater than zero");
        this.coreExecutor = coreExecutor;
        this.maxThreads = maxThreads;
    }

    @Override
    public void execute(Runnable task)
    {
        if (failed.get()) {
            throw new RejectedExecutionException("BoundedExecutor is in a failed state");
        }

        queue.add(task);

        int size = queueSize.incrementAndGet();
        if (size <= maxThreads) {
            // If able to grab a permit, then we are short exactly one draining thread
            try {
                coreExecutor.execute(drainQueueTask);
            }
            catch (Throwable e) {
                failed.set(true);
                log.error("BoundedExecutor state corrupted due to underlying executor failure");
                throw e;
            }
        }
    }

    /**
     * Drains the queue and runs each task one at a time.
     * Interrupt handling is similar to MoreExecutors#newSequentialExecutor.
     * Before running each task, clear the interrupt status then restore the interrupt status at the end.
     */
    private void drainQueue()
    {
        // INVARIANT: queue has at least one task available when this method is called
        boolean interrupted = false;
        try {
            do {
                interrupted = Thread.interrupted() || interrupted;
                try {
                    queue.poll().run();
                }
                catch (Throwable e) {
                    log.error(e, "Task failed");
                }
            }
            while (queueSize.getAndDecrement() > maxThreads);
        }
        finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
