package io.airlift.concurrent;

import com.google.common.base.Preconditions;
import io.airlift.log.Logger;

import javax.annotation.concurrent.ThreadSafe;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Math.max;
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

    private final Executor coreExecutor;
    private final int maxThreads;

    public BoundedExecutor(Executor coreExecutor, int maxThreads)
    {
        requireNonNull(coreExecutor, "coreExecutor is null");
        Preconditions.checkArgument(maxThreads > 0, "maxThreads must be greater than zero");
        this.coreExecutor = coreExecutor;
        this.maxThreads = maxThreads;
    }

    @Override
    public void execute(Runnable task)
    {
        queue.add(task);

        int size = queueSize.incrementAndGet();
        if (size <= maxThreads) {
            try {
                coreExecutor.execute(this::drainQueue);
            }
            catch (Throwable e) {
                decrementAndGetQueueSize();
                throw e;
            }
        }
    }

    private void drainQueue()
    {
        while (true) {
            Runnable task = queue.poll();

            if (task != null) {
                try {
                    task.run();
                }
                catch (Throwable e) {
                    log.error(e, "Task failed");
                }
            }

            if ((decrementAndGetQueueSize() == 0) && (task == null)) {
                return;
            }
        }
    }

    private int decrementAndGetQueueSize()
    {
        return queueSize.updateAndGet(current -> max(current - 1, 0));
    }
}
