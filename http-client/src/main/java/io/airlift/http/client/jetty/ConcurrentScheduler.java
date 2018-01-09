package io.airlift.http.client.jetty;

import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.thread.Scheduler;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.util.Objects.requireNonNull;

// based on ScheduledExecutorScheduler
class ConcurrentScheduler
        extends AbstractLifeCycle
        implements Scheduler
{
    private final int threadsPerScheduler;
    private final ScheduledExecutorService[] schedulers;
    private final ThreadFactory threadFactory;

    ConcurrentScheduler(int schedulerCount, int threadsPerScheduler, String threadBaseName)
    {
        checkArgument(schedulerCount > 0, "schedulerCount must be at least one");
        this.schedulers = new ScheduledThreadPoolExecutor[schedulerCount];
        checkArgument(threadsPerScheduler > 0, "threadsPerScheduler must be at least one");
        this.threadsPerScheduler = threadsPerScheduler;
        requireNonNull(threadBaseName, "threadBaseName is null");
        threadFactory = daemonThreadsNamed(threadBaseName + "-timeout-%s");
    }

    @Override
    protected void doStart()
    {
        for (int i = 0; i < schedulers.length; i++) {
            ScheduledThreadPoolExecutor scheduledExecutorService = new ScheduledThreadPoolExecutor(threadsPerScheduler, threadFactory);
            scheduledExecutorService.setRemoveOnCancelPolicy(true);
            schedulers[i] = scheduledExecutorService;
        }
    }

    @Override
    protected void doStop()
    {
        for (int i = 0; i < schedulers.length; i++) {
            schedulers[i].shutdownNow();
            schedulers[i] = null;
        }
    }

    @Override
    public Task schedule(Runnable task, long delay, TimeUnit unit)
    {
        ScheduledExecutorService scheduler = schedulers[ThreadLocalRandom.current().nextInt(schedulers.length)];
        if (scheduler == null) {
            return () -> false;
        }

        ScheduledFuture<?> result = scheduler.schedule(task, delay, unit);
        return () -> result.cancel(false);
    }
}
