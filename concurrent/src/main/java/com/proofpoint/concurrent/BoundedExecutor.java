package com.proofpoint.concurrent;

import com.google.common.base.Preconditions;
import com.proofpoint.log.Logger;

import javax.annotation.concurrent.ThreadSafe;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p/>
 * Guarantees that no more than maxThreads will be used to execute tasks submitted
 * through execute().
 * <p/>
 * There are a few interesting properties:
 * - Multiple BoundedExecutors over a single coreExecutor will have fair sharing
 * of the coreExecutor threads proportional to their relative maxThread counts, but
 * can use less if not as active.
 * - Tasks submitted to a BoundedExecutor is guaranteed to have those tasks handed to
 * threads in that order.
 * - Will not encounter starvation
 */
@ThreadSafe
public class BoundedExecutor
        implements Executor
{
    private static final Logger log = Logger.get(BoundedExecutor.class);

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final Runnable triggerTask = new Runnable()
    {
        @Override
        public void run()
        {
            executeOrMerge();
        }
    };

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
        queue.add(task);
        coreExecutor.execute(triggerTask);
        // INVARIANT: every enqueued task is matched with an executeOrMerge() triggerTask
    }

    private void executeOrMerge()
    {
        int size = queueSize.incrementAndGet();
        if (size <= maxThreads) {
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
}
