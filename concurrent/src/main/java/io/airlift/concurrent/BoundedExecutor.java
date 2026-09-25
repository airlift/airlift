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
     * Interrupt handling is similar to MoreExecutors#newSequentialExecutor with one key difference:
     * newSequentialExecutor restores any interrupt observed at any point while draining the queue,
     * which can propagate one task's own interrupt (e.g. a canceled FutureTask) onward to whatever
     * runs next on that thread. BoundedExecutor only restores interrupt status that was already present
     * before this batch began; an interrupt arising from a task during the batch is cleared and not
     * propagated.
     * For a BoundedExecutor wrapping a plain ThreadPoolExecutor, this distinction rarely matters:
     * ThreadPoolExecutor clears interrupt status before each task it runs, so any residual
     * flag left behind would be erased before it could be observed anyway.
     * <p>
     * Task {@link Exception}s are logged and swallowed so that a single failing task cannot stop the
     * batch. {@link Error}s (e.g. {@link OutOfMemoryError}) are considered non-recoverable and are
     * propagated to terminate the draining thread; before rethrowing, the queue accounting is kept
     * consistent and, if work remains, a replacement draining task is dispatched so that queued tasks
     * are not stranded.
     */
    private void drainQueue()
    {
        // INVARIANT: queue has at least one task available when this method is called
        boolean interruptedAtStart = Thread.interrupted();
        try {
            do {
                try {
                    queue.poll().run();
                }
                catch (Exception e) {
                    log.error(e, "Task failed");
                }
                finally {
                    Thread.interrupted();
                }
            }
            while (queueSize.getAndDecrement() > maxThreads);
        }
        catch (Error e) {
            // An Error will terminate this draining thread. Account for the failed task and, if there is
            // still more queued work than active drainers, hand the remainder off to another thread.
            if (queueSize.getAndDecrement() > maxThreads) {
                try {
                    coreExecutor.execute(drainQueueTask);
                }
                catch (Throwable t) {
                    failed.set(true);
                    log.error("BoundedExecutor state corrupted due to underlying executor failure");
                    e.addSuppressed(t);
                }
            }
            throw e;
        }
        finally {
            if (interruptedAtStart) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
