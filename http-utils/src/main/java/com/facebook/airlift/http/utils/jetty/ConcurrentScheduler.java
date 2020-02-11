package com.facebook.airlift.http.utils.jetty;

import com.facebook.airlift.concurrent.ConcurrentScheduledExecutor;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.thread.Scheduler;

import javax.annotation.concurrent.GuardedBy;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.max;
import static java.util.Objects.requireNonNull;

public class ConcurrentScheduler
        extends AbstractLifeCycle
        implements Scheduler
{
    private final int schedulerCount;
    private final int threadsPerScheduler;
    private final String threadBaseName;

    @GuardedBy("this")
    private volatile ConcurrentScheduledExecutor concurrentScheduler;

    public ConcurrentScheduler(
            int schedulerCount,
            int threadsPerScheduler,
            String threadBaseName)
    {
        checkArgument(schedulerCount > 0, "schedulerCount must be at least one");
        checkArgument(threadsPerScheduler > 0, "threadsPerScheduler must be at least one");

        this.schedulerCount = schedulerCount;
        this.threadsPerScheduler = threadsPerScheduler;
        this.threadBaseName = requireNonNull(threadBaseName, "threadBaseName is null");
    }

    public static ConcurrentScheduler createConcurrentScheduler(
            String threadBaseName,
            int concurrency,
            int totalThreads)
    {
        checkArgument(concurrency >= 1, "concurrency must be at least one");
        int threadsPerScheduler = max(1, totalThreads / concurrency);
        return new ConcurrentScheduler(concurrency, threadsPerScheduler, threadBaseName);
    }

    @Override
    protected void doStart()
    {
        synchronized (this) {
            if (concurrentScheduler == null) {
                concurrentScheduler = new ConcurrentScheduledExecutor(schedulerCount, threadsPerScheduler, threadBaseName, true);
            }
        }
    }

    @Override
    protected void doStop()
    {
        synchronized (this) {
            if (concurrentScheduler != null) {
                concurrentScheduler.shutdownNow();
                concurrentScheduler = null;
            }
        }
    }

    @Override
    public Task schedule(Runnable task, long delay, TimeUnit unit)
    {
        ConcurrentScheduledExecutor scheduler = this.concurrentScheduler;
        if (scheduler == null) {
            return () -> false;
        }
        // can throw RejectedExecutionException if it is shutting down, but that is ok
        ScheduledFuture<?> result = scheduler.schedule(task, delay, unit);
        return () -> result.cancel(false);
    }
}
