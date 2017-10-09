package io.airlift.http.client.jetty;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

import java.io.Closeable;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static io.airlift.concurrent.Threads.daemonThreadsNamed;
import static java.lang.Math.max;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

public final class JettyIoPool
        implements Closeable
{
    static {
        JettyLogging.setup();
    }

    private final String name;
    private final QueuedThreadPool executor;
    private final ByteBufferPool byteBufferPool;
    private final Scheduler scheduler;

    public JettyIoPool(String name, JettyIoPoolConfig config)
    {
        this.name = name;
        try {
            String baseName = "http-client-" + name;

            QueuedThreadPool threadPool = new QueuedThreadPool(config.getMaxThreads(), config.getMinThreads(), 60000, null);
            threadPool.setName(baseName);
            threadPool.setDaemon(true);
            threadPool.start();
            threadPool.setStopTimeout(2000);
            threadPool.setDetailedDump(true);
            executor = threadPool;

            if (config.getTimeoutConcurrency() == 1 && config.getTimeoutThreads() == 1) {
                scheduler = new ScheduledExecutorScheduler(baseName + "-scheduler", true, currentThread().getContextClassLoader());
            }
            else {
                scheduler = new ConcurrentScheduler(
                        config.getTimeoutConcurrency(),
                        max(1, config.getTimeoutThreads() / config.getTimeoutConcurrency()),
                        baseName + "-scheduler");
            }
            scheduler.start();

            byteBufferPool = new MappedByteBufferPool();
        }
        catch (Exception e) {
            close();
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()
    {
        try {
            closeQuietly(executor);
        }
        finally {
            closeQuietly(scheduler);
        }
    }

    private static void closeQuietly(LifeCycle service)
    {
        try {
            if (service != null) {
                service.stop();
            }
        }
        catch (Exception ignored) {
        }
    }

    public String getName()
    {
        return name;
    }

    public Executor getExecutor()
    {
        return executor;
    }

    public ByteBufferPool getByteBufferPool()
    {
        return byteBufferPool;
    }

    public Scheduler getScheduler()
    {
        return scheduler;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("name", name)
                .toString();
    }

    // based on ScheduledExecutorScheduler
    private static class ConcurrentScheduler
            extends AbstractLifeCycle
            implements Scheduler
    {
        private final int threadsPerScheduler;
        private final ScheduledExecutorService[] schedulers;
        private final ThreadFactory threadFactory;

        public ConcurrentScheduler(int schedulerCount, int threadsPerScheduler, String threadBaseName)
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
                throws Exception
        {
            for (int i = 0; i < schedulers.length; i++) {
                ScheduledThreadPoolExecutor scheduledExecutorService = new ScheduledThreadPoolExecutor(threadsPerScheduler, threadFactory);
                scheduledExecutorService.setRemoveOnCancelPolicy(true);
                schedulers[i] = scheduledExecutorService;
            }
        }

        @Override
        protected void doStop()
                throws Exception
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
}
