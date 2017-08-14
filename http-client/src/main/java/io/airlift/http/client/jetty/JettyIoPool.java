package io.airlift.http.client.jetty;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;

import java.io.Closeable;
import java.util.concurrent.Executor;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.lang.Thread.currentThread;

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

            scheduler = new ScheduledExecutorScheduler(baseName + "-scheduler", true, currentThread().getContextClassLoader());
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
}
